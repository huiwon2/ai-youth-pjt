package com.aiyouth.pjt.service.comparison;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.aiyouth.pjt.calculator.HomeCostCalculator;
import com.aiyouth.pjt.calculator.KoreanText;
import com.aiyouth.pjt.dto.response.PlanningResponse.Facility;
import com.aiyouth.pjt.dto.response.PlanningResponse.Home;
import com.aiyouth.pjt.dto.response.PlanningResponse.Route;
import com.aiyouth.pjt.dto.response.PlanningResponse.Session;
import com.aiyouth.pjt.dto.response.PlanningResponse.Warning;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.ComparisonHome;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.Cost;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.Recommendation;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.Scores;
import com.aiyouth.pjt.global.exception.ApiException;
import com.aiyouth.pjt.mapper.ComparisonRunMapper;
import com.aiyouth.pjt.model.ComparisonRun;
import com.aiyouth.pjt.model.enums.ComparisonStatus;
import com.aiyouth.pjt.model.enums.PriorityPreset;
import com.aiyouth.pjt.service.ComparisonInputs;
import com.aiyouth.pjt.service.ComparisonInputs.Candidate;
import com.aiyouth.pjt.service.ComparisonInputs.Snapshot;
import com.aiyouth.pjt.service.PlanningSessions;
import com.aiyouth.pjt.service.policy.PolicyEvaluationService;

import tools.jackson.databind.json.JsonMapper;

/**
 * API 10 후보 집 비교 생성·복원.
 * <ul>
 *   <li>입력(프로필·우선순위·후보·경로·시설)은 백엔드 1의 {@link ComparisonInputs#load}로 트랜잭션 밖에서 읽는다.
 *       외부 경로 조회가 길어도 세션 행을 잠그지 않기 위해서다.</li>
 *   <li>저장은 {@link ComparisonRunWriter}가 별도 트랜잭션에서 소유권·revision을 다시 확인한 뒤 한다.</li>
 *   <li>입력과 조회 결과로 보증금 부족분·월 주거비·참고 교통비를 계산한다.</li>
 *   <li>지원 판정 규칙과 점수 환산식이 확정될 때까지 지원 적용 비용·점수·순위를 null로 반환한다.</li>
 * </ul>
 * 이 클래스에는 트랜잭션을 걸지 않는다 (ComparisonInputs.load는 트랜잭션 안에서 호출할 수 없다).
 */
@Service
public class ComparisonService {

    private final ComparisonInputs inputs;
    private final PlanningSessions sessions;
    private final ComparisonRunWriter writer;
    private final ComparisonRunMapper runMapper;
    private final JsonMapper json;

    public ComparisonService(ComparisonInputs inputs, PlanningSessions sessions, ComparisonRunWriter writer,
                             ComparisonRunMapper runMapper, JsonMapper json) {
        this.inputs = inputs;
        this.sessions = sessions;
        this.writer = writer;
        this.runMapper = runMapper;
        this.json = json;
    }

    public ComparisonResponse create(UUID sessionId, String token, int inputRevision) {
        Snapshot snapshot = inputs.load(sessionId, token, inputRevision);
        List<Candidate> candidates = snapshot.candidates();
        if (candidates.size() < 2 || candidates.size() > 3) {
            throw ApiException.withFields(422, "HOME_COUNT_INVALID", "비교하려면 후보 집이 2~3곳 필요해요.",
                    List.of(new ApiException.FieldError("homes", "COUNT_2_TO_3")));
        }
        PriorityPreset preset = PriorityPreset.valueOf(snapshot.preferences().priorityPreset());
        Long availableCash = snapshot.profile() == null ? null : snapshot.profile().availableCashWon();
        Instant now = Instant.now();
        List<Warning> warnings = new ArrayList<>();

        List<ComparisonHome> homes = new ArrayList<>();
        for (Candidate candidate : candidates) {
            Home home = candidate.home();
            Route route = candidate.route();
            Cost cost = HomeCostCalculator.calculate(home.depositWon(), home.monthlyRentWon(), home.maintenanceWon(),
                    availableCash, null, route == null ? null : route.oneWayFareWon());
            homes.add(new ComparisonHome(home.homeId(), home.slot(), home.label(), home.location(), cost, route,
                    candidate.facilities(), List.of(), Scores.pending(), null));
            addHomeWarnings(warnings, homes.size() - 1, home, route, candidate);
        }
        addComparisonWarnings(warnings, homes);

        Recommendation recommendation = recommend(homes);
        ComparisonStatus status = ComparisonStatus.PARTIAL;
        String version = PolicyEvaluationService.RULES_VERSION;
        UUID comparisonId = UUID.randomUUID();
        ComparisonResponse response = new ComparisonResponse(comparisonId, snapshot.inputRevision(),
                snapshot.assessmentDate(), version, status, true, preset, snapshot.preferences().weights(), homes,
                recommendation, warnings, now);

        ComparisonRun run = new ComparisonRun();
        run.setId(comparisonId);
        run.setSessionId(sessionId);
        run.setInputRevision(snapshot.inputRevision());
        run.setAssessmentDate(snapshot.assessmentDate());
        run.setCalculationVersion(version);
        run.setStatus(status);
        run.setInputSnapshot(json.writeValueAsString(inputSnapshot(snapshot)));
        // 집별 정책 판정에 카탈로그 정책을 쓰기 시작하면 {policy, catalog_hash} 원소를 넣는다 (ERD 7절)
        run.setPolicySnapshot("[]");
        run.setResultSnapshot(json.writeValueAsString(response.withCurrent(false)));
        run.setCalculatedAt(OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
        writer.save(sessionId, token, inputRevision, run);
        return response;
    }

    /** 저장된 결과를 다시 계산하지 않고 읽는다. isCurrent만 현재 입력과 비교해 정한다. */
    public ComparisonResponse get(UUID sessionId, String token, UUID comparisonId) {
        Session session = sessions.get(sessionId, token);
        ComparisonRun run = requireRun(sessionId, comparisonId);
        ComparisonResponse stored = json.readValue(run.getResultSnapshot(), ComparisonResponse.class);
        return stored.withCurrent(isCurrent(run, stored, session.revision(), session.assessmentDate()));
    }

    ComparisonRun requireRun(UUID sessionId, UUID comparisonId) {
        ComparisonRun run = runMapper.findByIdAndSession(comparisonId, sessionId);
        if (run == null) {
            throw new ApiException(404, "RESOURCE_NOT_FOUND", "비교 결과를 찾을 수 없어요. 후보 집 목록에서 다시 비교해 주세요.",
                    false);
        }
        return run;
    }

    /** 입력과 계산 버전이 같고 실제 공급원의 경로를 사용한 결과만 현재 결과로 본다. */
    boolean isCurrent(ComparisonRun run, ComparisonResponse stored, int revision, LocalDate assessmentDate) {
        return run.getInputRevision() == revision && run.getAssessmentDate().equals(assessmentDate)
                && PolicyEvaluationService.RULES_VERSION.equals(run.getCalculationVersion())
                && stored.homes().stream().allMatch(home -> home.route() == null || home.route().hasLiveSource());
    }

    static boolean hasDuration(Route route) {
        return route != null && route.durationSeconds() != null
                && ("OK".equals(route.status()) || "ESTIMATED".equals(route.status()));
    }

    private static void addHomeWarnings(List<Warning> warnings, int index, Home home, Route route,
                                        Candidate candidate) {
        String prefix = "homes[" + index + "]";
        if (home.maintenanceWon() == null) {
            warnings.add(new Warning("MAINTENANCE_UNKNOWN", prefix + ".cost.maintenanceWon",
                    home.label() + "의 관리비를 몰라 월세만 합산했어요."));
        }
        if (!hasDuration(route)) {
            warnings.add(new Warning("ROUTE_UNAVAILABLE", prefix + ".route",
                    home.label() + "의 이동 시간을 확인하지 못했어요."));
        }
        List<Facility> facilities = candidate.facilities() == null ? List.of() : candidate.facilities();
        if (facilities.stream().anyMatch(f -> "UNAVAILABLE".equals(f.status()))) {
            warnings.add(new Warning("FACILITIES_UNAVAILABLE", prefix + ".facilities",
                    home.label() + " 주변 시설 일부를 조회하지 못했어요."));
        }
        // 경로·시설 조회 단계의 경고를 후보 위치와 함께 전달한다
        if (candidate.warnings() != null) {
            for (Warning w : candidate.warnings()) {
                warnings.add(new Warning(w.code(), w.field() == null ? prefix : prefix + "." + w.field(), w.message()));
            }
        }
    }

    private static void addComparisonWarnings(List<Warning> warnings, List<ComparisonHome> homes) {
        if (homes.stream().allMatch(h -> h.cost().excludedUnknownMaintenance())) {
            warnings.add(new Warning("ALL_MAINTENANCE_UNKNOWN", null,
                    "모든 후보의 관리비를 몰라 추천을 보류했어요. 관리비를 알아본 뒤 입력해 주세요."));
        }
        warnings.add(new Warning("SCORE_FORMULA_PENDING", "homes[].scores",
                "점수 환산식이 확정되지 않아 점수와 순위를 보류했어요. 비용과 이동 시간 차이를 비교해 주세요."));
        warnings.add(new Warning("POLICY_RULES_PENDING", "homes[].policyResults",
                "집별 지원 판정 규칙이 확정되지 않아 지원 적용 비용을 계산하지 않았어요."));
    }

    /** 순위가 있는 집 중 1위를 추천한다. 이유는 저장된 비용·시간 값으로만 만든다. */
    static Recommendation recommend(List<ComparisonHome> homes) {
        List<ComparisonHome> ranked = homes.stream().filter(h -> h.rank() != null)
                .sorted(Comparator.comparingInt(ComparisonHome::rank)).toList();
        List<String> tradeoffs = new ArrayList<>();
        homes.stream().filter(h -> h.cost().excludedUnknownMaintenance())
                .forEach(h -> tradeoffs.add(KoreanText.topic(h.label()) + " 관리비를 확인하지 못해 순위에서 보류했어요."));
        if (ranked.isEmpty()) {
            return new Recommendation(null, List.of(), tradeoffs, List.of());
        }
        ComparisonHome top = ranked.get(0);
        List<String> reasons = new ArrayList<>();
        for (ComparisonHome other : ranked.subList(1, ranked.size())) {
            long costDiff = other.cost().monthlyHousingCostWon() - top.cost().monthlyHousingCostWon();
            if (costDiff > 0) {
                reasons.add(other.label() + "보다 한 달 " + KoreanText.won(costDiff) + " 적게 들어요.");
            } else if (costDiff < 0) {
                tradeoffs.add(other.label() + "보다 한 달 " + KoreanText.won(-costDiff) + " 더 들어요.");
            }
            if (hasDuration(top.route()) && hasDuration(other.route())) {
                int minuteDiff = KoreanText.minutes(top.route().durationSeconds())
                        - KoreanText.minutes(other.route().durationSeconds());
                if (minuteDiff > 0) {
                    tradeoffs.add(other.label() + "보다 편도 " + minuteDiff + "분 더 걸려요.");
                } else if (minuteDiff < 0) {
                    reasons.add(other.label() + "보다 편도 " + (-minuteDiff) + "분 짧아요.");
                }
            }
        }
        return new Recommendation(top.homeId(), reasons, tradeoffs, ranked.stream().map(ComparisonHome::homeId).toList());
    }

    private static Map<String, Object> inputSnapshot(Snapshot snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenario_type", "BASE");
        result.put("profile", snapshot.profile());
        result.put("preferences", snapshot.preferences());
        result.put("homes", snapshot.candidates().stream().map(Candidate::home).toList());
        return result;
    }
}
