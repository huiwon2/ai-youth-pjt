package com.aiyouth.pjt.service;

import com.aiyouth.pjt.dto.response.PlanningResponse.*;
import com.aiyouth.pjt.model.PlanningRows.SearchChoice;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class DemoPlaces {
    private static final SearchChoice DESTINATION = new SearchChoice("멀티캠퍼스 역삼", "서울 강남구 테헤란로 212",
            null, 127.039600248343, 37.5012767241426, true, null);
    private static final List<SearchChoice> HOMES = List.of(
            new SearchChoice("성수동 원룸", null, "서울 성동구 성수동2가 333-76", 127.052537326065, 37.5422139625325, true, null),
            new SearchChoice("역삼동 원룸", null, "서울 강남구 역삼동 778-26", 127.043981259813, 37.4961924178362, true, null),
            new SearchChoice("신림동 원룸", null, "서울 관악구 신림동 502-36", 126.916378173249, 37.4861842453111, true, null));

    List<SearchChoice> search(String kind, String query) {
        String needle = normalize(query);
        if (kind.equals("DESTINATION")) {
            return matches(DESTINATION, needle) ? List.of(DESTINATION) : List.of();
        }
        if (needle.endsWith("로") || needle.endsWith("길")) {
            return List.of(new SearchChoice(query, query, null, null, null, false, "BUILDING_NUMBER_REQUIRED"));
        }
        return HOMES.stream().filter(home -> matches(home, needle)).toList();
    }

    Route route(Location home, Location destination, Instant now) {
        if (destination != null && destination.equals(DESTINATION.location())) {
            if (home.equals(HOMES.get(0).location())) {
                return route("TRANSIT", 30, 1550, now);
            }
            if (home.equals(HOMES.get(1).location())) {
                return route("WALK", 13, 0, now);
            }
            if (home.equals(HOMES.get(2).location())) {
                return route("TRANSIT", 30, 1650, now);
            }
        }
        return new Route("UNAVAILABLE", null, "DEMO", null, null, null, null, List.of(), null, now);
    }

    private Route route(String mode, int minutes, int fare, Instant now) {
        return new Route("OK", mode, "DEMO", minutes * 60, null, fare, null, List.of(), null, now);
    }

    private static boolean matches(SearchChoice location, String needle) {
        return normalize(location.name()).contains(needle) || normalize(location.location().address()).contains(needle);
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", "").replace("서울특별시", "서울");
    }
}
