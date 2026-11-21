package com.aiyouth.pjt.service.policy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.aiyouth.pjt.dto.request.policy.PolicyEvaluationRequest;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * API 4 지원 조회 · API 5 가정 계산.
 * <p>
 * 세션 소유권(작업 쿠키)·만료·입력 버전은 {@link PlanningSessions#getForInput}이 확인한다.
 * 정책별 판정 규칙표가 확정되기 전에는 INCLUDE 정책을 CHECK_NEEDED·UNKNOWN으로 반환하고
 * 합계는 UNKNOWN/null로 둔다. 규칙이 확정되면 {@link #RULES_VERSION}을 올리고 판정 단계를 추가한다.
 */
@Service
public class PolicyEvaluationService {

    public static final String RULES_VERSION = "rules-pending:v0";
    private static final int EVIDENCE_PER_POLICY = 3;

    private final PlanningSessions sessions;
    private final PolicyMapper policyMapper;
    private final RagChunkMapper chunkMapper;
    private final JsonMapper json;

    public PolicyEvaluationService(PlanningSessions sessions, PolicyMapper policyMapper, RagChunkMapper chunkMapper,
                                   JsonMapper json) {
        this.sessions = sessions;
        this.policyMapper = policyMapper;
        this.chunkMapper = chunkMapper;
        this.json = json;
    }

    /** API 4: 저장된 프로필로 현재 지원을 조회한다. */
    public PolicyEvaluationResponse evaluate(UUID sessionId, String token, int inputRevision) {
        Session session = loadEvaluable(sessionId, token, inputRevision);
        return fromCatalog(session);
    }

    /** API 5: 가정 조건의 결과와 현재 결과의 차이. 저장된 프로필은 바꾸지 않는다. */
    public PolicyEvaluationResponse evaluateScenario(UUID sessionId, String token, PolicyEvaluationRequest request) {
        Session session = loadEvaluable(sessionId, token, request.inputRevision());
        PolicyEvaluationResponse response = fromCatalog(session);
        // 판정 규칙이 확정되기 전에는 가정 입력에 따른 금액과 정책 변화도 미확정이다.
        Long total = response.summary().potentialTotalWon();
        return response.withDelta(new Delta(total, total, List.of(), List.of(), List.of()));
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
}
