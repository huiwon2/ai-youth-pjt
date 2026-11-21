package com.aiyouth.pjt.service;

import com.aiyouth.pjt.dto.response.PlanningResponse.*;
import com.aiyouth.pjt.global.common.RequestFields;
import com.aiyouth.pjt.global.exception.ApiException;
import com.aiyouth.pjt.global.revision.Revisions;
import com.aiyouth.pjt.mapper.PlanningMapper;
import com.aiyouth.pjt.model.PlanningRows.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class HomeTransactions {
    private final PlanningMapper mapper;
    private final SessionState state;
    private final PlanningSessions sessions;
    private final LocationChoices choices;
    private final ObjectMapper json;

    public HomeTransactions(PlanningMapper mapper, SessionState state, PlanningSessions sessions,
                            LocationChoices choices, ObjectMapper json) {
        this.mapper = mapper;
        this.state = state;
        this.sessions = sessions;
        this.choices = choices;
        this.json = json;
    }

    @Transactional
    public PreviewInput previewInput(UUID sessionId, String token, int revision, UUID locationId) {
        SessionRow session = state.lockOwned(sessionId, token);
        Revisions.requireInput(revision, session.revision());
        ChosenLocation home = choices.resolve(sessionId, locationId, "HOME");
        PlaceRow place = mapper.findPlace(sessionId);
        ChosenLocation destination = state.destination(place);
        if (place == null || place.destinationVersion() == null || !destination.location().hasCoordinates()) {
            throw ApiException.missingDestination();
        }
        CommuteLimit limit = sessions.profile(session, place).commuteLimit();
        return new PreviewInput(home, destination, place.destinationVersion(), limit == null ? null : limit.minutes());
    }

    @Transactional
    public HomePreview storePreview(UUID sessionId, String token, int revision, PreviewInput input, HomeLookup lookup) {
        SessionRow session = state.lockOwned(sessionId, token);
        Revisions.requireInput(revision, session.revision());
        PlaceRow place = mapper.findPlace(sessionId);
        if (place == null || !input.destinationVersion().equals(place.destinationVersion())) {
            throw expiredPreview();
        }
        UUID id = UUID.randomUUID();
        OffsetDateTime now = state.now();
        OffsetDateTime expiry = now.plusMinutes(15);
        mapper.deleteExpiredPreviews(sessionId, now);
        mapper.insertPreview(new PreviewRow(id, sessionId, input.destinationVersion(),
                json.writeValueAsString(new PreviewDetails(input.home(), lookup)), expiry));
        return new HomePreview(id, expiry.toInstant(), input.home().location(),
                lookup.route().withCommuteLimit(input.commuteMinutes()), lookup.facilities(), lookup.warnings());
    }

    @Transactional
    public Session put(UUID sessionId, String token, String ifMatch, int slot, JsonNode body) {
        SessionRow session = state.lockOwned(sessionId, token);
        Revisions.requireMatch(ifMatch, session.revision());
        requireSlot(slot);
        RequestFields request = new RequestFields(body, "previewId", "label", "depositWon", "monthlyRentWon",
                "maintenanceWon", "housingType", "exclusiveAreaM2", "inputMethod");

        PreviewRow preview = mapper.findPreview(sessionId, request.id("previewId"));
        PlaceRow destination = mapper.findPlace(sessionId);
        if (preview == null || !preview.expiresAt().isAfter(state.now()) || destination == null
                || !preview.destinationVersion().equals(destination.destinationVersion())) {
            throw expiredPreview();
        }
        PreviewDetails details = json.readValue(preview.details(), PreviewDetails.class);
        if (!LocationChoices.KAKAO_SOURCE.equals(details.location().source())) {
            throw expiredPreview();
        }
        String label = request.has("label") ? request.text("label", 200) : details.location().location().address();
        long deposit = request.nonNegativeLong("depositWon");
        long rent = request.nonNegativeLong("monthlyRentWon");
        Long maintenance = request.has("maintenanceWon") && request.blank("maintenanceWon") ? null
                : request.nonNegativeLong("maintenanceWon");
        String housing = request.blank("housingType") ? null : request.oneOf("housingType", "JEONSE", "MONTHLY", "PUBLIC_RENTAL");
        BigDecimal area = request.blank("exclusiveAreaM2") ? null : request.positiveDecimal("exclusiveAreaM2");
        String method = request.has("inputMethod") ? request.oneOf("inputMethod", "MANUAL") : "MANUAL";
        if ("JEONSE".equals(housing) && rent != 0) {
            throw request.invalid("monthlyRentWon", "JEONSE_RENT_MUST_BE_ZERO");
        }

        HomeRow previous = mapper.findHomes(sessionId).stream().filter(row -> row.slotNo() == slot)
                .findFirst().orElse(null);
        boolean sameHome = previous != null && details.location().equals(state.homeLocation(previous));
        UUID homeId = sameHome ? previous.id() : UUID.randomUUID();
        HomeRow next = new HomeRow(homeId, sessionId, slot, label, details.location().location().address(), housing,
                deposit, rent, maintenance, area, method, json.writeValueAsString(details.location()),
                json.writeValueAsString(details.lookup()), preview.destinationVersion());
        if (sameHome && sameValues(previous, next, details.lookup())) {
            return sessions.response(session);
        }
        String provenance = json.writeValueAsString(Map.of("depositWon", "USER", "monthlyRentWon", "USER",
                "maintenanceWon", maintenance == null ? "UNKNOWN" : "USER",
                "exclusiveAreaM2", area == null ? "UNKNOWN" : "USER", "address", details.location().source()));
        mapper.saveHome(next, provenance, state.now());
        return sessions.response(state.advance(session));
    }

    @Transactional
    public Session delete(UUID sessionId, String token, String ifMatch, int slot) {
        SessionRow session = state.lockOwned(sessionId, token);
        Revisions.requireMatch(ifMatch, session.revision());
        requireSlot(slot);
        if (mapper.deleteHome(sessionId, slot) == 1) {
            session = state.advance(session);
        }
        return sessions.response(session);
    }

    @Transactional
    public Inputs snapshot(UUID sessionId, String token, int expectedRevision) {
        SessionRow session = state.lockOwned(sessionId, token);
        Revisions.requireInput(expectedRevision, session.revision());
        PlaceRow destination = mapper.findPlace(sessionId);
        return new Inputs(session, sessions.profile(session, destination), destination, mapper.findHomes(sessionId));
    }

    @Transactional
    public void finishLookup(UUID sessionId, String token, int expectedRevision, List<HomeLookupUpdate> updates) {
        Revisions.requireInput(expectedRevision, state.lockOwned(sessionId, token).revision());
        for (HomeLookupUpdate update : updates) {
            HomeRow previous = update.previous();
            mapper.cacheHomeLookup(previous.id(), json.writeValueAsString(update.lookup()), update.destinationVersion(),
                    previous.lookupDetails(), previous.routeDestinationVersion());
        }
    }

    private boolean sameValues(HomeRow previous, HomeRow next, HomeLookup lookup) {
        return state.home(previous).equals(state.home(next))
                && Objects.equals(previous.routeDestinationVersion(), next.routeDestinationVersion())
                && previous.lookupDetails() != null
                && json.readValue(previous.lookupDetails(), HomeLookup.class).equals(lookup);
    }

    private static void requireSlot(int slot) {
        if (slot < 1 || slot > 3) {
            throw ApiException.invalid("slot", "OUT_OF_RANGE");
        }
    }

    private static ApiException expiredPreview() {
        return new ApiException(409, "PREVIEW_EXPIRED", "집 주변 정보를 다시 조회해 주세요.", false);
    }

    public record PreviewInput(ChosenLocation home, ChosenLocation destination, UUID destinationVersion, Integer commuteMinutes) {}
    public record Inputs(SessionRow session, Profile profile, PlaceRow destination, List<HomeRow> homes) {}
}
