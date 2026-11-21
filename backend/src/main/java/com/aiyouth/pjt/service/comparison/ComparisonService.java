package com.aiyouth.pjt.service.comparison;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiyouth.pjt.calculator.HomeCostCalculator;
import com.aiyouth.pjt.calculator.KoreanText;
import com.aiyouth.pjt.dto.response.common.CommonTypes.Facility;
import com.aiyouth.pjt.dto.response.common.CommonTypes.Route;
import com.aiyouth.pjt.dto.response.common.CommonTypes.Warning;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.ComparisonHome;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.Cost;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.HomePolicyResult;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.Recommendation;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.Scores;
import com.aiyouth.pjt.dto.response.policy.PolicyEvaluationResponse.Benefit;
import com.aiyouth.pjt.global.exception.ApiException;
import com.aiyouth.pjt.global.exception.ErrorCode;
import com.aiyouth.pjt.global.revision.Revisions;
import com.aiyouth.pjt.mapper.ComparisonRunMapper;
import com.aiyouth.pjt.model.ComparisonRun;
import com.aiyouth.pjt.model.enums.ApplicationStatus;
import com.aiyouth.pjt.model.enums.ComparisonStatus;
import com.aiyouth.pjt.model.enums.EligibilityStatus;
import com.aiyouth.pjt.model.enums.RouteStatus;
import com.aiyouth.pjt.model.planning.HomeRow;
import com.aiyouth.pjt.service.demo.DemoFixture;
import com.aiyouth.pjt.service.demo.DemoScenarios;
import com.aiyouth.pjt.service.planning.HomeContextProvider;
import com.aiyouth.pjt.service.planning.HomeContextProvider.HomeContext;
import com.aiyouth.pjt.service.planning.PlanningContext;
import com.aiyouth.pjt.service.planning.PlanningContextReader;
import com.aiyouth.pjt.service.policy.PolicyEvaluationService;

import tools.jackson.databind.json.JsonMapper;

/**
 * API 10 후보 집 비교 생성·복원.
 * <ul>
 *   <li>비용(부족분·월 비용·지원 적용 예시·참고 교통비)은 항상 계산한다.</li>
 *   <li>경로·시설은 {@link HomeContextProvider}(백엔드 1)에서 받는다. 없으면 UNAVAILABLE.</li>
 *   <li>점수 환산식은 보류 상태라, 승인된 시연(BALANCED)이 아니면 점수·순위를 null로 두고 사유를 경고로 남긴다.</li>
 * </ul>
 */
@Service
public class ComparisonService {

    private final PlanningContextReader reader;
    private final HomeContextProvider homeContextProvider;
    private final DemoScenarios demo;
    private final ComparisonRunMapper runMapper;
    private final JsonMapper json;

    public ComparisonService(PlanningContextReader reader, HomeContextProvider homeContextProvider,
                             DemoScenarios demo, ComparisonRunMapper runMapper, JsonMapper json) {
        this.reader = reader;
        this.homeContextProvider = homeContextProvider;
        this.demo = demo;
        this.runMapper = runMapper;
        this.json = json;
    }

    @Transactional
    public ComparisonResponse create(UUID sessionId, int inputRevision) {
        PlanningContext ctx = reader.load(sessionId);
        Revisions.requireCurrentInput(inputRevision, ctx.revision());
        if (ctx.homes().size() < 2 || ctx.homes().size() > 3) {
            throw ApiException.field(ErrorCode.HOME_COUNT_INVALID, "비교하려면 후보 집이 2~3곳 필요해요.", "homes",
                    "COUNT_2_TO_3");
        }
        Optional<DemoFixture.Comparison> demoComparison = demo.comparison(ctx);
        boolean demoScores = demoComparison.isPresent()
                && demoComparison.get().scoredPreset().equals(ctx.priorityPreset().name());
        OffsetDateTime now = reader.now();
        List<Warning> warnings = new ArrayList<>();

        List<ComparisonHome> homes = new ArrayList<>();
        for (HomeRow home : ctx.homes()) {
            Optional<DemoFixture.Home> demoHome = demoComparison.flatMap(d -> d.homes().stream()
                    .filter(h -> h.slot() == home.getSlotNo()).findFirst());
            HomeContext homeContext = homeContextProvider.lookup(ctx, home);
            Route route = demoHome.map(h -> demoRoute(h, now)).orElse(homeContext.route());
            Long monthlySupport = demoComparison.map(DemoFixture.Comparison::monthlySupportWon).orElse(null);
            Cost cost = HomeCostCalculator.calculate(home.getDepositWon(), home.getMonthlyRentWon(),
                    home.getMaintenanceWon(), ctx.availableCashWon(), monthlySupport, route.oneWayFareWon());
            List<HomePolicyResult> policyResults = demoComparison.map(d -> List.of(demoPolicyResult(d)))
                    .orElse(List.of());
            Scores scores = demoScores && demoHome.isPresent() ? new Scores(null, null, null, demoHome.get().totalScore())
                    : Scores.pending();
            Integer rank = demoScores ? demoHome.map(DemoFixture.Home::rank).orElse(null) : null;
            homes.add(new ComparisonHome(home.getId(), home.getSlotNo(), home.getLabel(), homeContext.location(), cost,
                    route, homeContext.facilities(), policyResults, scores, rank));
            addHomeWarnings(warnings, homes.size() - 1, home, route, homeContext.facilities());
        }
        addComparisonWarnings(warnings, homes, demoComparison.isPresent(), demoScores);

        Recommendation recommendation = recommend(homes);
        ComparisonStatus status = isComplete(homes) ? ComparisonStatus.COMPLETE : ComparisonStatus.PARTIAL;
        String version = demoComparison.isPresent() ? demo.version() : PolicyEvaluationService.RULES_VERSION;
        UUID comparisonId = UUID.randomUUID();
        ComparisonResponse response = new ComparisonResponse(comparisonId, ctx.revision(), ctx.assessmentDate(),
                version, status, true, ctx.priorityPreset(), ctx.weights(), homes, recommendation, warnings, now);

        ComparisonRun run = new ComparisonRun();
        run.setId(comparisonId);
        run.setSessionId(sessionId);
        run.setInputRevision(ctx.revision());
        run.setAssessmentDate(ctx.assessmentDate());
        run.setCalculationVersion(version);
        run.setStatus(status);
        run.setInputSnapshot(json.writeValueAsString(inputSnapshot(ctx, demoComparison.isPresent())));
        // 집별 정책 판정에 카탈로그 정책을 쓰기 시작하면 {policy, catalog_hash} 원소를 넣는다 (ERD 7절)
        run.setPolicySnapshot("[]");
        run.setResultSnapshot(json.writeValueAsString(response.withCurrent(false)));
        run.setCalculatedAt(now);
        runMapper.insert(run);
        return response;
    }

    /** 저장된 결과를 다시 계산하지 않고 읽는다. isCurrent만 현재 입력과 비교해 정한다. */
    @Transactional(readOnly = true)
    public ComparisonResponse get(UUID sessionId, UUID comparisonId) {
        PlanningContext ctx = reader.load(sessionId);
        ComparisonRun run = requireRun(sessionId, comparisonId);
        ComparisonResponse stored = json.readValue(run.getResultSnapshot(), ComparisonResponse.class);
        return stored.withCurrent(isCurrent(run, ctx));
    }

    ComparisonRun requireRun(UUID sessionId, UUID comparisonId) {
        ComparisonRun run = runMapper.findByIdAndSession(comparisonId, sessionId);
        if (run == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "비교 결과를 찾을 수 없어요. 후보 집 목록에서 다시 비교해 주세요.");
        }
        return run;
    }

    /** 입력 버전·판정일·계산 버전이 모두 같아야 현재 결과다. */
    boolean isCurrent(ComparisonRun run, PlanningContext ctx) {
        boolean knownVersion = run.getCalculationVersion().equals(demo.version())
                || run.getCalculationVersion().equals(PolicyEvaluationService.RULES_VERSION);
        return run.getInputRevision() == ctx.revision() && run.getAssessmentDate().equals(ctx.assessmentDate())
                && knownVersion;
    }

    private static Route demoRoute(DemoFixture.Home h, OffsetDateTime now) {
        return new Route(RouteStatus.OK, h.routeMode(), "DEMO_FIXTURE", h.durationSeconds(), null, null, null,
                List.of(), null, now);
    }

    private static HomePolicyResult demoPolicyResult(DemoFixture.Comparison d) {
        return new HomePolicyResult(d.supportPolicyKey(), EligibilityStatus.CHECK_NEEDED, ApplicationStatus.UNKNOWN,
                List.of(), new Benefit(d.monthlySupportWon(), null, null, null),
                "시연 예시: 월 " + KoreanText.won(d.monthlySupportWon()) + "을 받는 기간의 참고값이에요.", List.of());
    }

    private static void addHomeWarnings(List<Warning> warnings, int index, HomeRow home, Route route,
                                        List<Facility> facilities) {
        String prefix = "homes[" + index + "]";
        if (home.getMaintenanceWon() == null) {
            warnings.add(new Warning("MAINTENANCE_UNKNOWN", prefix + ".cost.maintenanceWon",
                    home.getLabel() + "의 관리비를 몰라 월세만 합산했어요. 관리비를 입력하면 순위에 들어가요."));
        }
        if (!route.hasDuration()) {
            warnings.add(new Warning("ROUTE_UNAVAILABLE", prefix + ".route",
                    home.getLabel() + "의 이동 시간을 확인하지 못했어요."));
        }
        if (facilities.stream().anyMatch(f -> "UNAVAILABLE".equals(f.status()))) {
            warnings.add(new Warning("FACILITIES_UNAVAILABLE", prefix + ".facilities",
                    home.getLabel() + " 주변 시설 일부를 조회하지 못했어요."));
        }
    }

    private static void addComparisonWarnings(List<Warning> warnings, List<ComparisonHome> homes, boolean demoData,
                                              boolean demoScores) {
        if (homes.stream().allMatch(h -> h.cost().excludedUnknownMaintenance())) {
            warnings.add(new Warning("ALL_MAINTENANCE_UNKNOWN", null,
                    "모든 후보의 관리비를 몰라 추천을 보류했어요. 관리비를 알아본 뒤 입력해 주세요."));
        }
        if (!demoScores) {
            warnings.add(new Warning("SCORE_FORMULA_PENDING", "homes[].scores",
                    "점수 환산식이 확정되지 않아 점수와 순위를 보류했어요. 비용과 이동 시간 차이를 비교해 주세요."));
        }
        if (!demoData) {
            warnings.add(new Warning("POLICY_RULES_PENDING", "homes[].policyResults",
                    "집별 지원 판정 규칙이 확정되지 않아 지원 적용 비용을 계산하지 않았어요."));
        } else {
            warnings.add(new Warning("DEMO_DATA", null, "시연용 예시 데이터로 계산한 결과예요."));
        }
    }

    private static boolean isComplete(List<ComparisonHome> homes) {
        return homes.stream().allMatch(h -> !h.cost().excludedUnknownMaintenance() && h.route().hasDuration()
                && h.rank() != null);
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
            if (top.route().hasDuration() && other.route().hasDuration()) {
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

    private static Map<String, Object> inputSnapshot(PlanningContext ctx, boolean demoData) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("ageRange", ctx.ageRange());
        profile.put("monthlyIncomeWon", ctx.monthlyIncomeWon());
        profile.put("availableCashWon", ctx.availableCashWon());
        List<Map<String, Object>> homes = ctx.homes().stream().map(h -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("homeId", h.getId());
            m.put("slot", h.getSlotNo());
            m.put("label", h.getLabel());
            m.put("confirmedAddress", h.getConfirmedAddress());
            m.put("housingType", h.getHousingType());
            m.put("depositWon", h.getDepositWon());
            m.put("monthlyRentWon", h.getMonthlyRentWon());
            m.put("maintenanceWon", h.getMaintenanceWon());
            return m;
        }).toList();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("scenario_type", "BASE");
        snapshot.put("demo", demoData);
        snapshot.put("profile", profile);
        snapshot.put("destination", ctx.destination() == null ? null
                : Map.of("name", ctx.destination().getName(), "address", ctx.destination().getConfirmedAddress()));
        snapshot.put("homes", homes);
        snapshot.put("priorityPreset", ctx.priorityPreset());
        snapshot.put("weights", ctx.weights());
        return snapshot;
    }
}
