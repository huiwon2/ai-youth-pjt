package com.aiyouth.pjt.dto.response.policy;

import java.time.LocalDate;
import java.util.List;

import com.aiyouth.pjt.model.enums.ApplicationStatus;
import com.aiyouth.pjt.model.enums.EligibilityStatus;
import com.aiyouth.pjt.model.enums.PolicyGroup;
import com.aiyouth.pjt.model.enums.TotalStatus;

/**
 * API 4·5 응답 (PolicyEvaluation). delta는 가정 계산(API 5)에서만 객체다.
 * calculationVersion이 "demo:"로 시작하면 승인된 시연 데이터로 만든 결과다.
 */
public record PolicyEvaluationResponse(
        int inputRevision,
        LocalDate assessmentDate,
        String calculationVersion,
        Summary summary,
        List<PolicyItem> policies,
        List<Schedule> calendar,
        List<Schedule> checklist,
        List<Evidence> evidence,
        List<Conflict> conflicts,
        Delta delta) {

    public PolicyEvaluationResponse withDelta(Delta newDelta) {
        return new PolicyEvaluationResponse(inputRevision, assessmentDate, calculationVersion, summary, policies,
                calendar, checklist, evidence, conflicts, newDelta);
    }

    /** potentialTotalWon: null = 미확인, 0 = 판정 완료 후 확인된 0원. */
    public record Summary(Long potentialTotalWon, TotalStatus totalStatus, List<String> includedPolicyKeys,
                          int targetYear) {
    }

    public record PolicyItem(String policyKey, String name, PolicyGroup group, EligibilityStatus eligibilityStatus,
                             ApplicationStatus applicationStatus, List<Condition> conditions, Benefit benefit,
                             List<Round> rounds, String methodText, String sourceUrl, List<Evidence> evidence) {
    }

    public record Condition(String key, String label, String actual, String expected, EligibilityStatus status,
                            String reason, List<String> evidenceIds) {
    }

    public record Benefit(Long monthlyWon, Integer durationMonths, Long oneTimeWon, Long potentialTotalWon) {
        public static Benefit unknown() {
            return new Benefit(null, null, null, null);
        }
    }

    public record Round(String roundId, ApplicationStatus status, LocalDate startDate, LocalDate endDate,
                        String sourceUrl) {
    }

    public record Schedule(String kind, String title, String policyKey, LocalDate startDate, LocalDate endDate,
                           List<String> evidenceIds) {
    }

    public record Conflict(String includedPolicyKey, String excludedPolicyKey, String reason,
                           List<String> evidenceIds) {
    }

    public record Delta(Long baseTotalWon, Long scenarioTotalWon, List<String> addedPolicyKeys,
                        List<String> removedPolicyKeys, List<String> changedPolicyKeys) {
    }
}
