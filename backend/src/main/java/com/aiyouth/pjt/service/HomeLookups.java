package com.aiyouth.pjt.service;

import com.aiyouth.pjt.dto.response.PlanningResponse.*;
import com.aiyouth.pjt.external.kakao.KakaoLocal;
import com.aiyouth.pjt.external.kakao.KakaoLocal.Category;
import com.aiyouth.pjt.external.odsay.OdsayTransit;
import com.aiyouth.pjt.global.exception.ApiException;
import com.aiyouth.pjt.model.PlanningRows.ChosenLocation;
import com.aiyouth.pjt.model.PlanningRows.HomeLookup;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
class HomeLookups {
    private final KakaoLocal kakao;
    private final OdsayTransit odsay;
    private final Executor executor;

    HomeLookups(KakaoLocal kakao, OdsayTransit odsay, @Qualifier("homeLookupExecutor") Executor executor) {
        this.kakao = kakao;
        this.odsay = odsay;
        this.executor = executor;
    }

    HomeLookup lookup(ChosenLocation home, ChosenLocation destination, Instant now) {
        return refresh(home, destination, null, true, now);
    }

    HomeLookup refresh(ChosenLocation home, ChosenLocation destination, HomeLookup saved, boolean refreshRoute, Instant now) {
        if (!home.location().hasCoordinates()) {
            return assemble(route(home, destination, now), unavailableFacilities(), now);
        }
        CompletableFuture<Route> route = refreshRoute
                ? fetch(() -> route(home, destination, now), OdsayTransit.empty("UNAVAILABLE", null, now))
                : CompletableFuture.completedFuture(saved.route());
        List<Facility> previous = saved == null ? unavailableFacilities() : saved.facilities();
        List<CompletableFuture<Facility>> facilities = previous.stream().map(facility -> {
            if (!facility.status().equals("UNAVAILABLE")) {
                return CompletableFuture.completedFuture(facility);
            }
            Category category = Category.valueOf(facility.category());
            return fetch(() -> kakao.nearby(category, home.location()), facility);
        }).toList();
        return assemble(route.join(), facilities.stream().map(CompletableFuture::join).toList(),
                saved == null ? now : saved.queriedAt());
    }

    private Route route(ChosenLocation home, ChosenLocation destination, Instant now) {
        if (destination == null || !destination.location().hasCoordinates() || !home.location().hasCoordinates()) {
            return OdsayTransit.empty("UNAVAILABLE", null, now);
        }
        return odsay.route(home.location(), destination.location(), now);
    }

    private HomeLookup assemble(Route route, List<Facility> facilities, Instant facilityQueryTime) {
        List<Warning> warnings = new ArrayList<>();
        switch (route.status()) {
            case "ESTIMATED" -> warnings.add(new Warning("ROUTE_ESTIMATED", "route", "직선거리를 기준으로 추정한 도보 시간이에요."));
            case "NOT_FOUND" -> warnings.add(new Warning("ROUTE_NOT_FOUND", "route", "대중교통 경로를 찾지 못했어요."));
            case "UNAVAILABLE" -> warnings.add(new Warning("ROUTE_UNAVAILABLE", "route", "이동 경로를 확인하지 못했어요."));
            default -> {}
        }
        for (Facility facility : facilities) {
            if (facility.status().equals("UNAVAILABLE")) {
                warnings.add(new Warning("FACILITY_UNAVAILABLE", "facilities." + facility.category(),
                        "이 시설은 조회 결과를 확인하지 못했어요."));
            }
        }
        return new HomeLookup(route, facilities, List.copyOf(warnings), facilityQueryTime);
    }

    private List<Facility> unavailableFacilities() {
        return Arrays.stream(Category.values()).map(category -> category.empty("UNAVAILABLE")).toList();
    }

    private <T> CompletableFuture<T> fetch(Supplier<T> supplier, T fallback) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return supplier.get();
                } catch (ApiException error) {
                    return fallback;
                }
            }, executor).completeOnTimeout(fallback, 10, TimeUnit.SECONDS);
        } catch (RejectedExecutionException error) {
            return CompletableFuture.completedFuture(fallback);
        }
    }
}
