package com.aiyouth.pjt.controller.policy;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiyouth.pjt.dto.request.policy.PolicyQuestionRequest;
import com.aiyouth.pjt.dto.response.policy.PolicyAnswerResponse;
import com.aiyouth.pjt.global.auth.SessionAuth;
import com.aiyouth.pjt.service.policy.PolicyQuestionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** API 6 공고문 질문. */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
public class PolicyQuestionController {

    private final SessionAuth auth;
    private final PolicyQuestionService service;

    public PolicyQuestionController(SessionAuth auth, PolicyQuestionService service) {
        this.auth = auth;
        this.service = service;
    }

    @PostMapping("/policy-questions")
    public PolicyAnswerResponse ask(@PathVariable UUID sessionId, @Valid @RequestBody PolicyQuestionRequest body,
                                    HttpServletRequest request) {
        return service.ask(sessionId, auth.token(request), body);
    }
}
