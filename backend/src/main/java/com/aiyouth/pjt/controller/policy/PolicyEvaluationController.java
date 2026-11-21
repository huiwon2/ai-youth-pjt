package com.aiyouth.pjt.controller.policy;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aiyouth.pjt.dto.request.policy.PolicyEvaluationRequest;
import com.aiyouth.pjt.dto.response.policy.PolicyEvaluationResponse;
import com.aiyouth.pjt.global.auth.SessionAuth;
import com.aiyouth.pjt.global.exception.ApiException;
import com.aiyouth.pjt.service.policy.PolicyEvaluationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** API 4 지원 조회 · API 5 가정 계산. 작업 쿠키가 없거나 다른 세션 것이면 401. */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
public class PolicyEvaluationController {

    private final SessionAuth auth;
    private final PolicyEvaluationService service;

    public PolicyEvaluationController(SessionAuth auth, PolicyEvaluationService service) {
        this.auth = auth;
        this.service = service;
    }

    @GetMapping("/policy-evaluation")
    public PolicyEvaluationResponse evaluate(@PathVariable UUID sessionId, @RequestParam int inputRevision,
                                             HttpServletRequest request) {
        if (inputRevision < 0) {
            throw ApiException.invalid("inputRevision", "MIN_0");
        }
        return service.evaluate(sessionId, auth.token(request), inputRevision);
    }

    @PostMapping("/policy-evaluations")
    public PolicyEvaluationResponse evaluateScenario(@PathVariable UUID sessionId,
                                                     @Valid @RequestBody PolicyEvaluationRequest body,
                                                     HttpServletRequest request) {
        return service.evaluateScenario(sessionId, auth.token(request), body);
    }
}
