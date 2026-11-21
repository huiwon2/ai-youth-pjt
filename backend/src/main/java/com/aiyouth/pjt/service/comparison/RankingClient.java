package com.aiyouth.pjt.service.comparison;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.ComparisonHome;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.Scores;
import com.aiyouth.pjt.dto.response.PlanningResponse.Warning;
import com.aiyouth.pjt.service.ComparisonInputs.Snapshot;

@Component
public class RankingClient {
    static final String VERSION = "recommend-house:v1";
    private final RestClient client;

    RankingClient(@Value("${firsthome.recommend-url}") String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    record House(UUID id, long deposit_won, long rent_won, Long maintenance_won, Integer commute_minutes,
                 Integer supermarket_walk_minutes, Integer convenience_store_walk_minutes, Integer pharmacy_walk_minutes) {}
    record Input(List<House> houses, String priority, List<Integer> weights, Integer maxCommuteMinutes) {}
    record Ranked(UUID id, Integer rank, Scores scores, List<String> missing, List<String> excluded, List<String> estimated) {}
    record Output(List<Ranked> homes) {}

    private static Integer walk(ComparisonHome home, String... categories) {
        if (home.facilities() == null) return null;
        return home.facilities().stream().filter(f -> List.of(categories).contains(f.category())
                && "FOUND".equals(f.status()) && f.walkMinutes() != null)
                .map(f -> f.walkMinutes()).min(Integer::compareTo).orElse(null);
    }

    List<ComparisonHome> rank(Snapshot snapshot, List<ComparisonHome> homes, List<Warning> warnings) {
        var houses = homes.stream().map(h -> new House(h.homeId(), h.cost().depositWon(), h.cost().monthlyRentWon(),
                h.cost().maintenanceWon(), ComparisonService.hasDuration(h.route()) ? (int) Math.ceil(h.route().durationSeconds() / 60.0) : null,
                walk(h, "SUPERMARKET", "LARGE_MART"), walk(h, "CONVENIENCE_STORE"), walk(h, "PHARMACY"))).toList();
        var pref = snapshot.preferences();
        var weights = pref.weights();
        var custom = "CUSTOM".equals(pref.priorityPreset()) ?
                List.of(weights.cost().intValueExact(), weights.commute().intValueExact(), weights.convenience().intValueExact()) : null;
        var limit = snapshot.profile() == null ? null : snapshot.profile().commuteLimit();
        var input = new Input(houses, pref.priorityPreset(), custom, limit == null ? null : limit.minutes());
        Map<UUID, Ranked> rows;
        try {
            Output output = client.post().uri("/v1/recommendations").body(input).retrieve().body(Output.class);
            if (output == null || output.homes() == null) throw new IllegalStateException("missing response");
            rows = output.homes().stream().collect(Collectors.toMap(Ranked::id, Function.identity()));
            if (rows.size() != homes.size() || homes.stream().anyMatch(h -> {
                var row = rows.get(h.homeId());
                return row == null || row.estimated() == null || row.excluded() == null
                        || (row.rank() == null) != (row.scores() == null);
            })) throw new IllegalStateException("invalid ranking");
        } catch (RestClientException | IllegalStateException e) {
            warnings.add(new Warning("RANKING_UNAVAILABLE", "homes", "추천 서비스 응답을 확인할 수 없어 순위를 보류했어요."));
            return homes;
        }
        return homes.stream().map(h -> {
            var row = rows.get(h.homeId());
            if (!row.excluded().isEmpty()) warnings.add(new Warning("RANKING_LIMIT", "homes",
                    h.label() + "의 통근 시간이 설정한 한도를 넘어 순위에서 제외했어요."));
            if (row.rank() != null && !row.estimated().isEmpty()) warnings.add(new Warning("RANKING_ESTIMATED", "homes",
                    h.label() + "의 미확인 시설 정보는 추정 점수를 사용했어요."));
            return new ComparisonHome(h.homeId(), h.slot(), h.label(), h.location(), h.cost(), h.route(), h.facilities(),
                    h.policyResults(), row.scores() == null ? Scores.pending() : row.scores(), row.rank());
        }).toList();
    }
}
