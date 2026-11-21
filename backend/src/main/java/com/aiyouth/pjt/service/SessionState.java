package com.aiyouth.pjt.service;

import com.aiyouth.pjt.dto.response.PlanningResponse.*;
import com.aiyouth.pjt.global.auth.SessionAuth;
import com.aiyouth.pjt.mapper.PlanningMapper;
import com.aiyouth.pjt.model.PlanningRows.*;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class SessionState {
    private final PlanningMapper mapper;
    private final SessionAuth auth;
    private final ObjectMapper json;
    private final Clock clock;

    public SessionState(PlanningMapper mapper, SessionAuth auth, ObjectMapper json, Clock clock) {
        this.mapper = mapper;
        this.auth = auth;
        this.json = json;
        this.clock = clock;
    }

    // Call from the transaction that reads or mutates the versioned inputs.
    @Transactional(propagation = Propagation.MANDATORY)
    public SessionRow lockOwned(UUID id, String token) {
        SessionRow row = mapper.lockOwnedSession(id, auth.hash(token));
        OffsetDateTime now = now();
        if (row == null || (row.expiresAt() != null && !row.expiresAt().isAfter(now))) {
            throw auth.expired();
        }
        LocalDate date = now.atZoneSameInstant(clock.getZone()).toLocalDate();
        return row.assessmentDate().equals(date) ? row : advance(row, date, now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SessionRow advance(SessionRow row) {
        return advance(row, row.assessmentDate(), now());
    }

    private SessionRow advance(SessionRow row, LocalDate date, OffsetDateTime now) {
        int revision = mapper.advanceRevision(row.id(), date, now);
        return new SessionRow(row.id(), date, revision, row.priorityPreset(), row.scoreWeights(), row.expiresAt());
    }

    public OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public ChosenLocation destination(PlaceRow row) {
        if (row == null) {
            return null;
        }
        if (row.locationDetails() != null) {
            return json.readValue(row.locationDetails(), ChosenLocation.class);
        }
        return new ChosenLocation(new Location(row.name(), row.confirmedAddress(), null, null), null);
    }

    public ChosenLocation homeLocation(HomeRow row) {
        if (row.locationDetails() != null) {
            return json.readValue(row.locationDetails(), ChosenLocation.class);
        }
        return new ChosenLocation(new Location(row.label(), row.confirmedAddress(), null, null), null);
    }

    public Home home(HomeRow row) {
        return new Home(row.id(), row.slotNo(), row.label(), homeLocation(row).location(), row.depositWon(),
                row.monthlyRentWon(), row.maintenanceWon(), row.housingType(), row.exclusiveAreaM2(), row.inputMethod());
    }

    public Preferences preferences(SessionRow row) {
        return new Preferences(row.priorityPreset(), json.readValue(row.scoreWeights(), Weights.class));
    }
}
