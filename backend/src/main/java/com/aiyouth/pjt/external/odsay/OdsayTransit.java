package com.aiyouth.pjt.external.odsay;

import com.aiyouth.pjt.dto.response.PlanningResponse.*;
import com.aiyouth.pjt.external.GeoDistance;
import com.aiyouth.pjt.external.ProviderHttp;
import com.aiyouth.pjt.global.exception.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class OdsayTransit {
    private final ProviderHttp http;
    private final String endpoint;
    private final String key;

    public OdsayTransit(ProviderHttp http, @Value("${firsthome.places.odsay-key:}") String key,
                        @Value("${firsthome.places.odsay-url:https://api.odsay.com}") String endpoint) {
        this.http = http;
        this.endpoint = endpoint;
        this.key = key.strip();
    }

    public Route route(Location home, Location destination, Instant now) {
        int distance = GeoDistance.straightLineMeters(home, destination);
        if (key.isEmpty()) {
            return fallback(home, destination, distance, now);
        }
        try {
            JsonNode response = http.get(endpoint + "/v1/api/searchPubTransPathT",
                    Map.of("SX", home.longitude(), "SY", home.latitude(), "EX", destination.longitude(),
                            "EY", destination.latitude(), "SearchType", 0, "apiKey", key), Map.of());
            JsonNode error = response.path("error");
            if (error.isArray()) {
                error = error.path(0);
            }
            Integer code = providerInteger(error.path("code"));
            if (code != null && code == -98) {
                return estimate(home, destination, distance, now);
            }
            if (code != null && (code == -99 || code >= 3 && code <= 6)) {
                return distance <= 1000 ? estimate(home, destination, distance, now)
                        : empty("NOT_FOUND", "ODSAY", now);
            }
            JsonNode paths = response.path("result").path("path");
            if (!paths.isArray()) {
                return fallback(home, destination, distance, now);
            }
            if (paths.isEmpty()) {
                return distance <= 1000 ? estimate(home, destination, distance, now)
                        : empty("NOT_FOUND", "ODSAY", now);
            }
            for (JsonNode path : paths) {
                Route route = parse(path, now);
                if (route != null) {
                    return route;
                }
            }
        } catch (ApiException error) {
            return fallback(home, destination, distance, now);
        }
        return fallback(home, destination, distance, now);
    }

    private Route parse(JsonNode path, Instant now) {
        JsonNode info = path.path("info");
        Integer seconds = minutesAsSeconds(info.path("totalTime"));
        if (seconds == null) {
            return null;
        }
        Integer fare = nonNegative(info.path("payment"));
        Integer distance = nonNegative(info.path("totalDistance"));
        List<Segment> segments = new ArrayList<>();
        for (JsonNode segment : path.path("subPath")) {
            Integer traffic = providerInteger(segment.path("trafficType"));
            if (traffic == null || traffic < 1 || traffic > 3) {
                continue;
            }
            String mode = switch (traffic) {
                case 1 -> "SUBWAY";
                case 2 -> "BUS";
                default -> "WALK";
            };
            String line = traffic == 3 ? null : segment.path("lane").path(0)
                    .path(traffic == 1 ? "name" : "busNo").asString(null);
            segments.add(new Segment(mode, line, minutesAsSeconds(segment.path("sectionTime"))));
        }
        return new Route("OK", "TRANSIT", "ODSAY", seconds, distance, fare, null,
                List.copyOf(segments), null, now);
    }

    private static Route fallback(Location home, Location destination, int distance, Instant now) {
        return distance <= 1000 ? estimate(home, destination, distance, now) : empty("UNAVAILABLE", null, now);
    }

    public static Route empty(String status, String source, Instant now) {
        return new Route(status, null, source, null, null, null, null, List.of(), null, now);
    }

    private static Route estimate(Location home, Location destination, int distance, Instant now) {
        int seconds = GeoDistance.walkSeconds(distance);
        Geometry line = new Geometry("LineString", List.of(List.of(home.longitude(), home.latitude()),
                List.of(destination.longitude(), destination.latitude())));
        return new Route("ESTIMATED", "WALK", "DISTANCE_ESTIMATE", seconds, distance, null, null,
                List.of(new Segment("WALK", null, seconds)), line, now);
    }

    private static Integer minutesAsSeconds(JsonNode node) {
        Integer minutes = nonNegative(node);
        return minutes == null || minutes > Integer.MAX_VALUE / 60 ? null : minutes * 60;
    }

    private static Integer nonNegative(JsonNode node) {
        if (node.isNumber()) {
            double number = node.asDouble();
            return Double.isFinite(number) && number >= 0 && number <= Integer.MAX_VALUE
                    ? (int) Math.round(number) : null;
        }
        Integer value = providerInteger(node);
        return value == null || value < 0 ? null : value;
    }

    private static Integer providerInteger(JsonNode node) {
        if (node.isIntegralNumber() && node.canConvertToInt()) {
            return node.asInt();
        }
        if (node.isString()) {
            try {
                return Integer.valueOf(node.asString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
