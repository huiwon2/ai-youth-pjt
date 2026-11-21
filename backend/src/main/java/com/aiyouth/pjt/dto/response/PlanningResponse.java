package com.aiyouth.pjt.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PlanningResponse {
    private PlanningResponse() {}

    public record Session(UUID sessionId, LocalDate assessmentDate, int revision, Instant expiresAt,
                          Profile profile, Progress progress, List<Home> homes,
                          Preferences preferences, UUID latestComparisonId) {}

    public record Profile(Integer birthYear, Boolean birthdayPassedThisYear, LocalDate birthdayAnswerAsOfDate,
                          String employmentStatus, Location frequentPlace, Long availableCashWon,
                          Income income, CommuteLimit commuteLimit, MoveIn moveIn, Boolean ownsHome,
                          String parentAddressStatus, Age age, Long normalizedMonthlyIncomeWon) {}

    public record Age(int minYears, int maxYears, Integer exactYears) {}
    public record Income(String period, long amountWon) {}
    public record CommuteLimit(String type, @JsonInclude(JsonInclude.Include.NON_NULL) Integer minutes) {}
    public record MoveIn(String type, LocalDate asOfDate, String startMonth, String endMonth) {}
    public record Progress(int answeredCount, int requiredCount, List<String> missingFields,
                           boolean canEvaluate, boolean canCompare) {}
    public record Preferences(String priorityPreset, Weights weights) {}
    public record Weights(BigDecimal cost, BigDecimal commute, BigDecimal convenience) {}

    public record Location(String name, String address, Double longitude, Double latitude) {
        public boolean hasCoordinates() {
            return longitude != null && latitude != null;
        }
    }

    public record LocationSearch(List<LocationItem> items, String dataSource) {}
    public record LocationItem(UUID locationId, String name, String roadAddress, String parcelAddress,
                               Double longitude, Double latitude, boolean selectable, String reason) {}

    public record Home(UUID homeId, int slot, String label, Location location, long depositWon,
                       long monthlyRentWon, Long maintenanceWon, String housingType,
                       BigDecimal exclusiveAreaM2, String inputMethod) {}

    public record HomePreview(UUID previewId, Instant expiresAt, Location location,
                              Route route, List<Facility> facilities, List<Warning> warnings) {}

    public record Route(String status, String mode, String source, Integer durationSeconds,
                        Integer distanceMeters, Integer oneWayFareWon, Long overLimitSeconds,
                        List<Segment> segments, Geometry geometry, Instant queriedAt) {
        public boolean hasLiveSource() {
            return source == null || "ODSAY".equals(source) || "DISTANCE_ESTIMATE".equals(source);
        }

        public Route withCommuteLimit(Integer minutes) {
            Long overLimit = durationSeconds == null || minutes == null
                    ? null : Math.max(0L, durationSeconds - minutes * 60L);
            return new Route(status, mode, source, durationSeconds, distanceMeters, oneWayFareWon,
                    overLimit, segments, geometry, queriedAt);
        }
    }

    public record Segment(String mode, String lineName, Integer durationSeconds) {}
    public record Geometry(String type, List<List<Double>> coordinates) {}
    public record Facility(String category, String status, String name, Location location,
                           Integer distanceMeters, Integer walkMinutes, String walkTimeSource,
                           int searchRadiusMeters) {}
    public record Warning(String code, String field, String message) {}
}
