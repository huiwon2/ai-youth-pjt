package com.aiyouth.pjt.external.kakao;

import com.aiyouth.pjt.dto.response.PlanningResponse.Facility;
import com.aiyouth.pjt.dto.response.PlanningResponse.Location;
import com.aiyouth.pjt.external.GeoDistance;
import com.aiyouth.pjt.external.ProviderHttp;
import com.aiyouth.pjt.model.PlanningRows.SearchChoice;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class KakaoLocal {
    public static final int SEARCH_RADIUS = 2000;
    private static final Pattern STREET_ONLY = Pattern.compile("(?:^|.*\\s)[^\\s]+(?:대로|로|길)(?:\\s*\\([^)]*\\))?$");
    private final ProviderHttp http;
    private final String endpoint;
    private final String key;

    public KakaoLocal(ProviderHttp http, @Value("${firsthome.places.kakao-key:}") String key,
                      @Value("${firsthome.places.kakao-url:https://dapi.kakao.com}") String endpoint) {
        this.http = http;
        this.endpoint = endpoint;
        this.key = key.strip();
    }

    public List<SearchChoice> search(String kind, String query) {
        boolean home = kind.equals("HOME");
        List<SearchChoice> choices = new ArrayList<>();
        JsonNode documents = call(home ? "address" : "keyword", Map.of("query", query, "size", 15)).path("documents");
        boolean streetOnly = home && STREET_ONLY.matcher(query).matches();
        for (JsonNode document : documents) {
            SearchChoice choice = home ? address(document, streetOnly) : destination(document);
            if (choice != null && !choices.contains(choice)) {
                choices.add(choice);
            }
        }
        return List.copyOf(choices);
    }

    public Facility nearby(Category category, Location center) {
        for (int page = 1; page <= 3; page++) {
            String parameter = category.group == null ? "query" : "category_group_code";
            JsonNode response = call(category.group == null ? "keyword" : "category", Map.of(
                    parameter, category.group == null ? category.keyword : category.group, "x", center.longitude(),
                    "y", center.latitude(), "radius", SEARCH_RADIUS, "sort", "distance", "size", 15, "page", page));
            for (JsonNode document : response.path("documents")) {
                String type = document.path("category_name").asString("");
                if (category.detail == null || category.detail.matcher(type).find()) {
                    Facility facility = facility(category, center, document);
                    if (facility != null) {
                        return facility;
                    }
                    return category.empty("NOT_FOUND");
                }
            }
            JsonNode end = response.path("meta").path("is_end");
            if (!end.isBoolean()) {
                throw ProviderHttp.unavailable();
            }
            if (end.asBoolean()) {
                return category.empty("NOT_FOUND");
            }
        }
        return category.empty("UNAVAILABLE");
    }

    private Facility facility(Category category, Location center, JsonNode document) {
        SearchChoice place = destination(document);
        if (place == null || !place.selectable()) {
            throw ProviderHttp.unavailable();
        }
        Location location = place.location();
        int distance = GeoDistance.straightLineMeters(center, location);
        JsonNode reported = document.path("distance");
        if (reported.isTextual() && reported.asString().matches("[0-9]{1,4}")) {
            distance = Integer.parseInt(reported.asString());
        }
        if (distance > SEARCH_RADIUS) {
            return null;
        }
        return new Facility(category.name(), "FOUND", location.name(), location, distance,
                (GeoDistance.walkSeconds(distance) + 59) / 60, "DISTANCE_ESTIMATE", SEARCH_RADIUS);
    }

    private JsonNode call(String method, Map<String, ?> params) {
        if (key.isEmpty()) {
            throw ProviderHttp.unavailable();
        }
        JsonNode response = http.get(endpoint + "/v2/local/search/" + method + ".json",
                params, Map.of("Authorization", "KakaoAK " + key));
        if (!response.path("documents").isArray()) {
            throw ProviderHttp.unavailable();
        }
        return response;
    }

    private SearchChoice destination(JsonNode document) {
        String name = string(document, "place_name");
        String road = string(document, "road_address_name");
        String parcel = string(document, "address_name");
        if (name == null || (road == null && parcel == null)) {
            return null;
        }
        Double longitude = GeoDistance.coordinate(document.path("x"), 180);
        Double latitude = GeoDistance.coordinate(document.path("y"), 90);
        boolean selectable = longitude != null && latitude != null;
        return new SearchChoice(name, road, parcel, longitude, latitude, selectable,
                selectable ? null : "COORDINATES_REQUIRED");
    }

    private SearchChoice address(JsonNode document, boolean streetOnly) {
        JsonNode road = document.path("road_address");
        JsonNode parcel = document.path("address");
        boolean hasRoadNumber = positiveNumber(road, "main_building_no");
        boolean hasParcelNumber = positiveNumber(parcel, "main_address_no");
        String roadName = string(road, "address_name");
        String parcelName = string(parcel, "address_name");
        String name = string(road, "building_name");
        if (name == null) {
            name = string(document, "address_name");
        }
        if (name == null) {
            return null;
        }
        // Prefer a verified building number when only the parcel address is complete.
        if (!hasRoadNumber && hasParcelNumber) {
            roadName = null;
        }
        Double longitude = GeoDistance.coordinate(document.path("x"), 180);
        Double latitude = GeoDistance.coordinate(document.path("y"), 90);
        boolean hasBuilding = !streetOnly && ((hasRoadNumber && roadName != null)
                || (hasParcelNumber && parcelName != null));
        boolean selectable = hasBuilding && longitude != null && latitude != null;
        String reason = selectable ? null : (hasBuilding ? "COORDINATES_REQUIRED" : "BUILDING_NUMBER_REQUIRED");
        return new SearchChoice(name, roadName, parcelName, longitude, latitude, selectable, reason);
    }

    private static boolean positiveNumber(JsonNode node, String field) {
        String number = string(node, field);
        return number != null && number.matches("[0-9]*[1-9][0-9]*");
    }

    private static String string(JsonNode node, String field) {
        String value = node.path(field).asString("");
        return value.isBlank() ? null : value;
    }

    public enum Category {
        SUBWAY("SW8", null, null),
        LARGE_MART("MT1", null, null),
        SUPERMARKET(null, "슈퍼마켓", "슈퍼마켓"),
        CONVENIENCE_STORE("CS2", null, null),
        PHARMACY("PM9", null, null),
        INTERNAL_MEDICINE(null, "내과", ">\\s*내과(?:\\s*>|$)"),
        GYM(null, "헬스장", "헬스클럽|헬스장|피트니스"),
        PARK(null, "공원", ">\\s*[^>]*공원(?:\\s*>|$)");

        private final String group;
        private final String keyword;
        private final Pattern detail;

        Category(String group, String keyword, String detail) {
            this.group = group;
            this.keyword = keyword;
            this.detail = detail == null ? null : Pattern.compile(detail);
        }

        public Facility empty(String status) {
            return new Facility(name(), status, null, null, null, null, null, SEARCH_RADIUS);
        }
    }
}
