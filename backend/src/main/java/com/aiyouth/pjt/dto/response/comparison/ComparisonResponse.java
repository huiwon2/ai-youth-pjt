package com.aiyouth.pjt.dto.response.comparison;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.aiyouth.pjt.dto.response.PlanningResponse.Facility;
import com.aiyouth.pjt.dto.response.PlanningResponse.Location;
import com.aiyouth.pjt.dto.response.PlanningResponse.Route;
import com.aiyouth.pjt.dto.response.PlanningResponse.Warning;
import com.aiyouth.pjt.dto.response.PlanningResponse.Weights;
import com.aiyouth.pjt.dto.response.policy.Evidence;
import com.aiyouth.pjt.dto.response.policy.PolicyEvaluationResponse.Benefit;
import com.aiyouth.pjt.dto.response.policy.PolicyEvaluationResponse.Condition;
import com.aiyouth.pjt.model.enums.ApplicationStatus;
import com.aiyouth.pjt.model.enums.ComparisonStatus;
import com.aiyouth.pjt.model.enums.EligibilityStatus;
import com.aiyouth.pjt.model.enums.PriorityPreset;

/**
 * API 10 응답 (Comparison). result_snapshot에 isCurrent=false로 저장하고, 조회 시 isCurrent만 다시 계산한다.
 * 위치·경로·시설·경고·가중치 타입은 백엔드 1의 PlanningResponse와 같은 것을 쓴다.
 */
public record ComparisonResponse(
        UUID comparisonId,
        int inputRevision,
        LocalDate assessmentDate,
        String calculationVersion,
        ComparisonStatus status,
        boolean isCurrent,
        PriorityPreset priorityPreset,
        Weights weights,
        List<ComparisonHome> homes,
        Recommendation recommendation,
        List<Warning> warnings,
        Instant calculatedAt) {

    public ComparisonResponse withCurrent(boolean current) {
        return new ComparisonResponse(comparisonId, inputRevision, assessmentDate, calculationVersion, status, current,
                priorityPreset, weights, homes, recommendation, warnings, calculatedAt);
    }

    public record ComparisonHome(UUID homeId, int slot, String label, Location location, Cost cost, Route route,
                                 List<Facility> facilities, List<HomePolicyResult> policyResults, Scores scores,
                                 Integer rank) {
    }

    /**
     * monthlyHousingCostWon = 월세 + 확인된 관리비. 관리비 미확인이면 월세만 넣고 excludedUnknownMaintenance=true.
     * monthlyTransportFareWon은 참고 항목으로 주거비에 더하지 않는다.
     */
    public record Cost(long depositWon, Long depositShortfallWon, long monthlyRentWon, Long maintenanceWon,
                      long monthlyHousingCostWon, boolean excludedUnknownMaintenance, Long monthlyAfterSupportWon,
                      Long monthlyTransportFareWon) {
    }

    public record HomePolicyResult(String policyKey, EligibilityStatus eligibilityStatus,
                                   ApplicationStatus applicationStatus, List<Condition> conditions, Benefit benefit,
                                   String reason, List<Evidence> evidence) {
    }

    /** 추천 서비스에서 받은 정수 점수. 순위가 보류된 집은 모두 null. */
    public record Scores(Integer cost, Integer commute, Integer convenience, Integer total) {
        public static Scores pending() {
            return new Scores(null, null, null, null);
        }
    }

    public record Recommendation(UUID homeId, List<String> reasons, List<String> tradeoffs, List<UUID> rankedHomeIds) {
    }
}
