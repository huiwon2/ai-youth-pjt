package com.aiyouth.pjt.controller.policy;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aiyouth.pjt.dto.request.policy.PolicyEvaluationRequest;
import com.aiyouth.pjt.dto.response.policy.PolicyEvaluationResponse;
import com.aiyouth.pjt.global.exception.ApiException;
import com.aiyouth.pjt.global.exception.ErrorCode;
import com.aiyouth.pjt.service.policy.PolicyEvaluationService;

import jakarta.validation.Valid;

/** API 4 지원 조회 · API 5 가정 계산. */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
public class PolicyEvaluationController {

    private final PolicyEvaluationService service;

    public PolicyEvaluationController(PolicyEvaluationService service) {
        this.service = service;
    }

    @GetMapping("/policy-evaluation")
    public ResponseEntity<PolicyEvaluationResponse> evaluate(@PathVariable UUID sessionId,
                                                             @RequestParam int inputRevision) {
        if (inputRevision < 0) {
            throw ApiException.field(ErrorCode.FIELD_INVALID, "입력 버전은 0 이상이어야 해요.", "inputRevision", "MIN_0");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.evaluate(sessionId, inputRevision));
    }

    @PostMapping("/policy-evaluations")
    public ResponseEntity<PolicyEvaluationResponse> evaluateScenario(@PathVariable UUID sessionId,
                                                                     @Valid @RequestBody PolicyEvaluationRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.evaluateScenario(sessionId, request));
    }
}
