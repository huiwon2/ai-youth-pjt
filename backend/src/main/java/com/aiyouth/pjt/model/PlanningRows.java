package com.aiyouth.pjt.model;

import com.aiyouth.pjt.dto.response.PlanningResponse.Facility;
import com.aiyouth.pjt.dto.response.PlanningResponse.Location;
import com.aiyouth.pjt.dto.response.PlanningResponse.Route;
import com.aiyouth.pjt.dto.response.PlanningResponse.Warning;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PlanningRows {
    private PlanningRows() {}

    public record SessionRow(UUID id, LocalDate assessmentDate, int revision, String priorityPreset,
                             String scoreWeights, OffsetDateTime expiresAt) {}
    public record ProfileRow(UUID sessionId, Integer ageYears, LocalDate ageAsOfDate, String employmentStatus,
                             Long availableCashWon, Long monthlyIncomeWon, Integer maxCommuteMinutes,
                             String desiredMoveInMonth, String additionalAnswers, OffsetDateTime confirmedAt) {}
    public record PlaceRow(UUID sessionId, String name, String confirmedAddress, String locationDetails,
                           UUID destinationVersion) {}
    public record HomeRow(UUID id, UUID sessionId, int slotNo, String label, String confirmedAddress,
                          String housingType, long depositWon, long monthlyRentWon, Long maintenanceWon,
                          BigDecimal exclusiveAreaM2, String inputMethod, String locationDetails,
                          String lookupDetails, UUID routeDestinationVersion) {}
    public record ChoiceRow(UUID id, UUID sessionId, String kind, String details, OffsetDateTime expiresAt) {}
    public record PreviewRow(UUID id, UUID sessionId, UUID destinationVersion, String details,
                             OffsetDateTime expiresAt) {}

    public record SearchChoice(String name, String roadAddress, String parcelAddress, Double longitude,
                               Double latitude, boolean selectable, String reason) {
        public Location location() {
            return new Location(name, roadAddress == null ? parcelAddress : roadAddress, longitude, latitude);
        }
    }

    public record ChosenLocation(Location location, String source) {}
    public record PreviewDetails(ChosenLocation location, HomeLookup lookup) {}
    public record HomeLookup(Route route, List<Facility> facilities, List<Warning> warnings, Instant queriedAt) {}
    public record HomeLookupUpdate(HomeRow previous, HomeLookup lookup, UUID destinationVersion) {}
}
