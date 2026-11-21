package com.aiyouth.pjt.dto.response.common;

import java.time.OffsetDateTime;
import java.util.List;

import com.aiyouth.pjt.model.enums.FacilityCategory;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.aiyouth.pjt.model.enums.PriorityPreset;
import com.aiyouth.pjt.model.enums.RouteStatus;

/**
 * API 명세 「공통 응답 자료형」 중 여러 응답이 같이 쓰는 타입.
 * Location·Route·Facility는 백엔드 1의 HomePreview와 같은 모양이다. 공용 타입이 생기면 그쪽으로 합친다.
 */
public final class CommonTypes {

    private CommonTypes() {
    }

    public record Weights(int cost, int commute, int convenience) {
        public static Weights of(PriorityPreset preset) {
            return new Weights(preset.cost(), preset.commute(), preset.convenience());
        }

        @JsonIgnore
        public boolean isValid() {
            return cost >= 0 && cost <= 100 && commute >= 0 && commute <= 100
                    && convenience >= 0 && convenience <= 100 && cost + commute + convenience == 100;
        }
    }

    public record Warning(String code, String field, String message) {
    }

    public record Location(String name, String address, Double longitude, Double latitude) {
    }

    public record Segment(String mode, String lineName, Integer durationSeconds) {
    }

    public record Geometry(String type, List<double[]> coordinates) {
    }

    public record Route(RouteStatus status, String mode, String source, Integer durationSeconds,
                        Integer distanceMeters, Integer oneWayFareWon, Integer overLimitSeconds,
                        List<Segment> segments, Geometry geometry, OffsetDateTime queriedAt) {

        public static Route unavailable(OffsetDateTime now) {
            return new Route(RouteStatus.UNAVAILABLE, null, null, null, null, null, null, List.of(), null, now);
        }

        @JsonIgnore
        public boolean hasDuration() {
            return durationSeconds != null && (status == RouteStatus.OK || status == RouteStatus.ESTIMATED);
        }
    }

    public record Facility(FacilityCategory category, String status, String name, Location location,
                           Integer distanceMeters, Integer walkMinutes, String walkTimeSource,
                           int searchRadiusMeters) {

        public static Facility unavailable(FacilityCategory category) {
            return new Facility(category, "UNAVAILABLE", null, null, null, null, null, 2000);
        }
    }
}
