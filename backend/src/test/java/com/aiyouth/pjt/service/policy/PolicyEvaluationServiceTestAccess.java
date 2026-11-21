package com.aiyouth.pjt.service.policy;

import com.aiyouth.pjt.dto.response.policy.PolicyEvaluationResponse;

public final class PolicyEvaluationServiceTestAccess {

    private PolicyEvaluationServiceTestAccess() {
    }

    public static PolicyEvaluationResponse.Delta delta(PolicyEvaluationResponse base, PolicyEvaluationResponse scenario) {
        return PolicyEvaluationService.delta(base, scenario);
    }
}
