package com.aiyouth.pjt.service;

import com.aiyouth.pjt.dto.response.PlanningResponse.*;
import com.aiyouth.pjt.model.PlanningRows.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class ComparisonInputs {
    private final SessionState state;
    private final HomeTransactions homes;
    private final HomeLookups lookups;
    private final ObjectMapper json;

    public ComparisonInputs(SessionState state, HomeTransactions homes, HomeLookups lookups, ObjectMapper json) {
        this.state = state;
        this.homes = homes;
        this.lookups = lookups;
        this.json = json;
    }

    // A comparison writer still verifies the revision in its own transaction.
    @Transactional(propagation = Propagation.NEVER)
    public Snapshot load(UUID sessionId, String token, int inputRevision) {
        var inputs = homes.snapshot(sessionId, token, inputRevision);
        var session = inputs.session();
        var destination = state.destination(inputs.destination());
        UUID destinationVersion = inputs.destination() == null ? null : inputs.destination().destinationVersion();
        CommuteLimit commute = inputs.profile().commuteLimit();
        Integer minutes = commute == null ? null : commute.minutes();
        Instant now = state.now().toInstant();
        List<Candidate> candidates = new ArrayList<>();
        List<HomeLookupUpdate> updates = new ArrayList<>();
        for (HomeRow row : inputs.homes()) {
            ChosenLocation home = state.homeLocation(row);
            HomeLookup saved = row.lookupDetails() == null ? null : json.readValue(row.lookupDetails(), HomeLookup.class);
            HomeLookup lookup = currentLookup(home, destination, destinationVersion, row, saved, now);
            if (!lookup.equals(saved) || !Objects.equals(row.routeDestinationVersion(), destinationVersion)) {
                updates.add(new HomeLookupUpdate(row, lookup, destinationVersion));
            }
            candidates.add(new Candidate(state.home(row), lookup.route().withCommuteLimit(minutes),
                    lookup.facilities(), lookup.warnings()));
        }
        homes.finishLookup(sessionId, token, inputRevision, updates);
        return new Snapshot(session.id(), session.revision(), session.assessmentDate(), inputs.profile(),
                state.preferences(session), List.copyOf(candidates));
    }

    private HomeLookup currentLookup(ChosenLocation home, ChosenLocation destination, UUID destinationVersion,
                                     HomeRow row, HomeLookup saved, Instant now) {
        if (saved == null || !fresh(saved.queriedAt(), now)) {
            return lookups.lookup(home, destination, now);
        }
        Route route = saved.route();
        boolean refreshRoute = !Objects.equals(destinationVersion, row.routeDestinationVersion())
                || !fresh(route.queriedAt(), now) || route.status().equals("UNAVAILABLE");
        boolean refreshFacilities = !"DEMO".equals(home.source()) && saved.facilities().stream()
                .anyMatch(facility -> facility.status().equals("UNAVAILABLE"));
        return refreshRoute || refreshFacilities ? lookups.refresh(home, destination, saved, refreshRoute, now) : saved;
    }

    private static boolean fresh(Instant queriedAt, Instant now) {
        return queriedAt != null && !queriedAt.isAfter(now) && queriedAt.plus(Duration.ofHours(24)).isAfter(now);
    }

    public record Candidate(Home home, Route route, List<Facility> facilities, List<Warning> warnings) {}
    public record Snapshot(UUID sessionId, int inputRevision, LocalDate assessmentDate, Profile profile,
                           Preferences preferences, List<Candidate> candidates) {}
}
