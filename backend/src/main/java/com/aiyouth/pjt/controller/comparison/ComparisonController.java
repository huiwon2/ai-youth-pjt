package com.aiyouth.pjt.controller.comparison;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiyouth.pjt.dto.request.comparison.ComparisonQuestionRequest;
import com.aiyouth.pjt.dto.request.comparison.ComparisonRequest;
import com.aiyouth.pjt.dto.response.comparison.ComparisonAnswerResponse;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse;
import com.aiyouth.pjt.global.auth.SessionAuth;
import com.aiyouth.pjt.service.comparison.ComparisonQuestionService;
import com.aiyouth.pjt.service.comparison.ComparisonService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** API 10 비교 생성·복원 · API 12 비교 후속 질문. */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/comparisons")
public class ComparisonController {

    private final SessionAuth auth;
    private final ComparisonService comparisonService;
    private final ComparisonQuestionService questionService;

    public ComparisonController(SessionAuth auth, ComparisonService comparisonService,
                                ComparisonQuestionService questionService) {
        this.auth = auth;
        this.comparisonService = comparisonService;
        this.questionService = questionService;
    }

    @PostMapping
    public ResponseEntity<ComparisonResponse> create(@PathVariable UUID sessionId,
                                                     @Valid @RequestBody ComparisonRequest body,
                                                     HttpServletRequest request) {
        ComparisonResponse result = comparisonService.create(sessionId, auth.token(request), body.inputRevision());
        URI location = URI.create("/api/v1/sessions/" + sessionId + "/comparisons/" + result.comparisonId());
        return ResponseEntity.created(location).body(result);
    }

    @GetMapping("/{comparisonId}")
    public ComparisonResponse get(@PathVariable UUID sessionId, @PathVariable UUID comparisonId,
                                  HttpServletRequest request) {
        return comparisonService.get(sessionId, auth.token(request), comparisonId);
    }

    @PostMapping("/{comparisonId}/questions")
    public ComparisonAnswerResponse ask(@PathVariable UUID sessionId, @PathVariable UUID comparisonId,
                                        @Valid @RequestBody ComparisonQuestionRequest body,
                                        HttpServletRequest request) {
        return questionService.ask(sessionId, auth.token(request), comparisonId, body.inputRevision(),
                body.question());
    }
}
