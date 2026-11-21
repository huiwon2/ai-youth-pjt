package com.aiyouth.pjt.service.demo;

import java.util.List;

import com.aiyouth.pjt.dto.response.policy.PolicyEvaluationResponse.Conflict;
import com.aiyouth.pjt.dto.response.policy.PolicyEvaluationResponse.PolicyItem;
import com.aiyouth.pjt.model.enums.TotalStatus;

/** resources/demo/demo-fixture.json 구조. 승인된 시연 값만 담는다. */
public record DemoFixture(String version, String source, List<String> notes, Condition condition,
                          List<Evaluation> evaluations, Comparison comparison) {

    public record Condition(int ageYears, long monthlyIncomeWon, long availableCashWon, String destinationKeyword) {
    }

    public record Evaluation(int ageYears, Long potentialTotalWon, TotalStatus totalStatus,
                             List<String> includedPolicyKeys, List<PolicyItem> policies, List<Conflict> conflicts) {
    }

    public record Comparison(String scoredPreset, long monthlySupportWon, String supportPolicyKey, List<Home> homes) {
    }

    public record Home(int slot, long monthlyRentWon, Long maintenanceWon, String routeMode, int durationSeconds,
                       Integer totalScore, Integer rank) {
    }
}
