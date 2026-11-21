package com.aiyouth.pjt.service.policy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.aiyouth.pjt.dto.request.policy.PolicyEvaluationRequest;
import com.aiyouth.pjt.dto.request.policy.PolicyEvaluationRequest.Overrides;
import com.aiyouth.pjt.dto.response.PlanningResponse.Session;
import com.aiyouth.pjt.dto.response.policy.Evidence;
import com.aiyouth.pjt.dto.response.policy.PolicyEvaluationResponse;
import com.aiyouth.pjt.dto.response.policy.PolicyEvaluationResponse.Benefit;
import com.aiyouth.pjt.dto.response.policy.PolicyEvaluationResponse.Delta;
import com.aiyouth.pjt.dto.response.policy.PolicyEvaluationResponse.PolicyItem;
import com.aiyouth.pjt.dto.response.policy.PolicyEvaluationResponse.Summary;
import com.aiyouth.pjt.global.exception.ApiException;
import com.aiyouth.pjt.mapper.PolicyMapper;
import com.aiyouth.pjt.mapper.RagChunkMapper;
import com.aiyouth.pjt.model.Policy;
import com.aiyouth.pjt.model.RagChunk;
import com.aiyouth.pjt.model.enums.ApplicationStatus;
import com.aiyouth.pjt.model.enums.EligibilityStatus;
import com.aiyouth.pjt.model.enums.PolicyGroup;
import com.aiyouth.pjt.model.enums.ReviewDecision;
import com.aiyouth.pjt.model.enums.TotalStatus;
import com.aiyouth.pjt.service.PlanningSessions;
import com.aiyouth.pjt.service.demo.DemoFixture;
import com.aiyouth.pjt.service.demo.DemoScenarios;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * API 4 지원 조회 · API 5 가정 계산.
 * <p>
 * 세션 소유권(작업 쿠키)·만료·입력 버전은 {@link PlanningSessions#getForInput}이 확인한다.
 * 정책별 판정 규칙표는 보류 상태다. 승인된 시연 조건이 아니면 INCLUDE 정책을 모두 CHECK_NEEDED·UNKNOWN으로
 * 내보내고 합계는 UNKNOWN/null로 둔다. 규칙이 확정되면 {@link #RULES_VERSION}을 올리고 판정 단계를 추가한다.
 */
@Service
public class PolicyEvaluationService {

    public static final String RULES_VERSION = "rules-pending:v0";
    private static final int EVIDENCE_PER_POLICY = 3;

    private final PlanningSessions sessions;
    private final PolicyMapper policyMapper;
    private final RagChunkMapper chunkMapper;
    private final DemoScenarios demo;
    private final JsonMapper json;

    public PolicyEvaluationService(PlanningSessions sessions, PolicyMapper policyMapper, RagChunkMapper chunkMapper,
                                   DemoScenarios demo, JsonMapper json) {
        this.sessions = sessions;
        this.policyMapper = policyMapper;
        this.chunkMapper = chunkMapper;
        this.demo = demo;
        this.json = json;
    }

    /** API 4: 저장된 프로필로 현재 지원을 조회한다. */
    public PolicyEvaluationResponse evaluate(UUID sessionId, String token, int inputRevision) {
        Session session = loadEvaluable(sessionId, token, inputRevision);
        return demo.baseEvaluation(session.profile()).map(d -> fromDemo(session, d)).orElseGet(() -> fromCatalog(session));
    }

    /** API 5: 가정 조건의 결과와 현재 결과의 차이. 저장된 프로필은 바꾸지 않는다. */
    public PolicyEvaluationResponse evaluateScenario(UUID sessionId, String token, PolicyEvaluationRequest request) {
        Session session = loadEvaluable(sessionId, token, request.inputRevision());
        Overrides overrides = request.overrides();
        PolicyEvaluationResponse base = demo.baseEvaluation(session.profile()).map(d -> fromDemo(session, d))
                .orElseGet(() -> fromCatalog(session));
        if (overrides == null || overrides.isEmpty()) {
            return base.withDelta(delta(base, base));
        }
        Optional<DemoFixture.Evaluation> demoScenario = demo.scenarioEvaluation(session.profile(), overrides);
        PolicyEvaluationResponse scenario = demoScenario.map(d -> fromDemo(session, d))
                .orElseGet(() -> fromCatalog(session));
        return scenario.withDelta(delta(base, scenario));
    }

    private Session loadEvaluable(UUID sessionId, String token, int inputRevision) {
        Session session = sessions.getForInput(sessionId, token, inputRevision);
        if (!session.progress().canEvaluate()) {
            List<ApiException.FieldError> fields = session.progress().missingFields().stream()
                    .map(f -> new ApiException.FieldError("profile." + f, "REQUIRED")).toList();
            throw ApiException.withFields(422, "PROFILE_INCOMPLETE", "내 상황에서 입력하지 않은 항목이 있어요.", fields);
        }
        return session;
    }

    private PolicyEvaluationResponse fromDemo(Session session, DemoFixture.Evaluation d) {
        return new PolicyEvaluationResponse(session.revision(), session.assessmentDate(), demo.version(),
                new Summary(d.potentialTotalWon(), d.totalStatus(), d.includedPolicyKeys(),
                        session.assessmentDate().getYear()),
                d.policies(), List.of(), List.of(), List.of(), d.conflicts(), null);
    }

    /** 실제 카탈로그 경로. 검증된 규칙이 없으므로 자격·금액·접수 상태를 확정하지 않는다. */
    private PolicyEvaluationResponse fromCatalog(Session session) {
        List<Policy> policies = policyMapper.findByReviewDecision(ReviewDecision.INCLUDE);
        Map<String, List<Evidence>> evidence = evidenceByPolicy(policies);
        List<PolicyItem> items = new ArrayList<>();
        for (Policy p : policies) {
            items.add(new PolicyItem(p.getPolicyKey(), p.getName(), PolicyGroup.CHECK_NEEDED,
                    EligibilityStatus.CHECK_NEEDED, ApplicationStatus.UNKNOWN, List.of(), Benefit.unknown(), List.of(),
                    methodText(p), p.getSourceUrl(), evidence.getOrDefault(p.getPolicyKey(), List.of())));
        }
        List<Evidence> allEvidence = evidence.values().stream().flatMap(List::stream).toList();
        return new PolicyEvaluationResponse(session.revision(), session.assessmentDate(), RULES_VERSION,
                new Summary(null, TotalStatus.UNKNOWN, List.of(), session.assessmentDate().getYear()),
                items, List.of(), List.of(), allEvidence, List.of(), null);
    }

    private Map<String, List<Evidence>> evidenceByPolicy(List<Policy> policies) {
        if (policies.isEmpty()) {
            return Map.of();
        }
        Set<String> keys = policies.stream().map(Policy::getPolicyKey).collect(Collectors.toSet());
        Map<String, List<Evidence>> result = new LinkedHashMap<>();
        for (RagChunk chunk : chunkMapper.findVerifiedBySourceKeys(keys)) {
            List<Evidence> list = result.computeIfAbsent(chunk.getSourceKey(), k -> new ArrayList<>());
            if (list.size() < EVIDENCE_PER_POLICY) {
                list.add(Evidence.from(chunk));
            }
        }
        return result;
    }

    private String methodText(Policy policy) {
        if (policy.getApplication() == null) {
            return null;
        }
        JsonNode method = json.readTree(policy.getApplication()).get("method_text");
        return method != null && method.isString() ? method.stringValue() : null;
    }

    static Delta delta(PolicyEvaluationResponse base, PolicyEvaluationResponse scenario) {
        List<String> baseKeys = base.summary().includedPolicyKeys();
        List<String> scenarioKeys = scenario.summary().includedPolicyKeys();
        List<String> added = scenarioKeys.stream().filter(k -> !baseKeys.contains(k)).toList();
        List<String> removed = baseKeys.stream().filter(k -> !scenarioKeys.contains(k)).toList();
        Map<String, PolicyItem> baseItems = base.policies().stream()
                .collect(Collectors.toMap(PolicyItem::policyKey, p -> p, (a, b) -> a));
        List<String> changed = scenario.policies().stream()
                .filter(p -> baseItems.containsKey(p.policyKey()))
                .filter(p -> !added.contains(p.policyKey()) && !removed.contains(p.policyKey()))
                .filter(p -> {
                    PolicyItem b = baseItems.get(p.policyKey());
                    return b.eligibilityStatus() != p.eligibilityStatus() || !b.benefit().equals(p.benefit());
                })
                .map(PolicyItem::policyKey)
                .toList();
        return new Delta(base.summary().potentialTotalWon(), scenario.summary().potentialTotalWon(), added, removed,
                changed);
    }
}
