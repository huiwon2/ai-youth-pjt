package com.aiyouth.pjt.service;

import com.aiyouth.pjt.dto.response.PlanningResponse.*;
import com.aiyouth.pjt.global.auth.SessionAuth;
import com.aiyouth.pjt.global.common.RequestFields;
import com.aiyouth.pjt.global.revision.Revisions;
import com.aiyouth.pjt.mapper.PlanningMapper;
import com.aiyouth.pjt.model.PlanningRows.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class PlanningSessions {
    private final PlanningMapper mapper;
    private final SessionState state;
    private final SessionAuth auth;
    private final LocationChoices choices;
    private final ObjectMapper json;
    private final Duration ttl;

    public PlanningSessions(PlanningMapper mapper, SessionState state, SessionAuth auth, LocationChoices choices,
                            ObjectMapper json, @Value("${firsthome.session.ttl-days:7}") long ttlDays) {
        if (ttlDays <= 0) {
            throw new IllegalArgumentException("Session lifetime must be positive");
        }
        this.mapper = mapper;
        this.state = state;
        this.auth = auth;
        this.choices = choices;
        this.json = json;
        this.ttl = Duration.ofDays(ttlDays);
    }

    @Transactional
    public CreatedSession create(JsonNode body) {
        new RequestFields(body);
        OffsetDateTime now = state.now();
        UUID id = UUID.randomUUID();
        String token = auth.issue();
        mapper.insertSession(id, auth.hash(token), state.today(), now.plus(ttl), now);
        return new CreatedSession(response(state.lockOwned(id, token)), auth.cookie(id, token, ttl));
    }

    @Transactional
    public Session get(UUID id, String token) {
        return response(state.lockOwned(id, token));
    }

    @Transactional
    public Session getForInput(UUID id, String token, int inputRevision) {
        SessionRow row = state.lockOwned(id, token);
        Revisions.requireInput(inputRevision, row.revision());
        return response(row);
    }

    @Transactional
    public Session patchProfile(UUID id, String token, String ifMatch, JsonNode body) {
        SessionRow session = state.lockOwned(id, token);
        Revisions.requireMatch(ifMatch, session.revision());
        RequestFields input = new RequestFields(body, ProfileAnswers.FIELDS);
        if (input.isEmpty()) {
            throw input.invalid("profile", "EMPTY_PATCH");
        }

        ProfileRow currentRow = mapper.findProfile(id);
        PlaceRow placeRow = mapper.findPlace(id);
        ChosenLocation currentPlace = state.destination(placeRow);
        ChosenLocation updatedPlace = currentPlace;
        if (input.has("frequentPlace")) {
            updatedPlace = input.blank("frequentPlace") ? null : choices.resolve(id,
                    input.nested("frequentPlace", "locationId").id("locationId"), "DESTINATION");
        }
        String employment = currentRow == null ? null : currentRow.employmentStatus();
        if (input.has("employmentStatus")) {
            employment = input.blank("employmentStatus") ? null :
                    input.oneOf("employmentStatus", "EMPLOYED", "STUDENT", "JOB_SEEKER", "OTHER");
        }
        Long cash = currentRow == null ? null : currentRow.availableCashWon();
        if (input.has("availableCashWon")) {
            cash = input.blank("availableCashWon") ? null : input.nonNegativeLong("availableCashWon");
        }

        ObjectNode priorAnswers = ProfileAnswers.read(json, currentRow);
        ObjectNode answers = priorAnswers.deepCopy();
        ProfileAnswers.patch(input, answers, session.assessmentDate(), json);
        boolean destinationChanged = !Objects.equals(currentPlace, updatedPlace);
        if (!destinationChanged && priorAnswers.equals(answers)
                && Objects.equals(employment, currentRow == null ? null : currentRow.employmentStatus())
                && Objects.equals(cash, currentRow == null ? null : currentRow.availableCashWon())
                && !clearsLegacyValue(input, currentRow)) {
            return response(session);
        }

        Long legacyIncome = currentRow == null || input.has("income") ? null : currentRow.monthlyIncomeWon();
        Integer legacyCommute = currentRow == null || input.has("commuteLimit") ? null : currentRow.maxCommuteMinutes();
        ProfileRow draft = new ProfileRow(id, null, null, employment, cash, legacyIncome, legacyCommute,
                null, json.writeValueAsString(answers), null);
        Location location = updatedPlace == null ? null : updatedPlace.location();
        Profile updated = ProfileAnswers.profile(draft, location, session.assessmentDate(), json);
        OffsetDateTime now = state.now();
        Integer exactAge = updated.age() == null ? null : updated.age().exactYears();
        mapper.saveProfile(new ProfileRow(id, exactAge, exactAge == null ? null : session.assessmentDate(),
                employment, cash, updated.normalizedMonthlyIncomeWon(),
                updated.commuteLimit() == null ? null : updated.commuteLimit().minutes(),
                updated.moveIn() == null ? null : updated.moveIn().startMonth(), draft.additionalAnswers(),
                ProfileAnswers.progress(updated, 0).canEvaluate() ? now : null), now);
        if (destinationChanged) {
            if (location == null) {
                mapper.deletePlace(id);
            } else {
                mapper.savePlace(new PlaceRow(id, location.name(), location.address(),
                        json.writeValueAsString(updatedPlace), UUID.randomUUID()), now);
            }
        }
        return response(state.advance(session));
    }

    public Session response(SessionRow row) {
        PlaceRow place = mapper.findPlace(row.id());
        Profile profile = profile(row, place);
        List<Home> homes = mapper.findHomes(row.id()).stream().map(state::home).toList();
        return new Session(row.id(), row.assessmentDate(), row.revision(),
                row.expiresAt() == null ? null : row.expiresAt().toInstant(), profile,
                ProfileAnswers.progress(profile, homes.size()), homes, state.preferences(row),
                mapper.latestComparison(row.id()));
    }

    public Profile profile(SessionRow row, PlaceRow place) {
        ChosenLocation chosen = state.destination(place);
        return ProfileAnswers.profile(mapper.findProfile(row.id()), chosen == null ? null : chosen.location(),
                row.assessmentDate(), json);
    }

    private boolean clearsLegacyValue(RequestFields input, ProfileRow row) {
        return row != null && ((input.has("income") && input.blank("income") && row.monthlyIncomeWon() != null)
                || (input.has("commuteLimit") && input.blank("commuteLimit") && row.maxCommuteMinutes() != null));
    }

    public record CreatedSession(Session session, ResponseCookie cookie) {}
}
