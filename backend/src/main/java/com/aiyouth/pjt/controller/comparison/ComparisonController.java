package com.aiyouth.pjt.controller.comparison;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.CacheControl;
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
import com.aiyouth.pjt.service.comparison.ComparisonQuestionService;
import com.aiyouth.pjt.service.comparison.ComparisonService;

import jakarta.validation.Valid;

/** API 10 비교 생성·복원 · API 12 비교 후속 질문. */
@RestController
@RequestMapping("/api/v1/sessions/{sessionId}/comparisons")
public class ComparisonController {

    private final ComparisonService comparisonService;
    private final ComparisonQuestionService questionService;

    public ComparisonController(ComparisonService comparisonService, ComparisonQuestionService questionService) {
        this.comparisonService = comparisonService;
        this.questionService = questionService;
    }

    @PostMapping
    public ResponseEntity<ComparisonResponse> create(@PathVariable UUID sessionId,
                                                     @Valid @RequestBody ComparisonRequest request) {
        ComparisonResponse body = comparisonService.create(sessionId, request.inputRevision());
        URI location = URI.create("/api/v1/sessions/" + sessionId + "/comparisons/" + body.comparisonId());
        return ResponseEntity.created(location).cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping("/{comparisonId}")
    public ResponseEntity<ComparisonResponse> get(@PathVariable UUID sessionId, @PathVariable UUID comparisonId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(comparisonService.get(sessionId, comparisonId));
    }

    @PostMapping("/{comparisonId}/questions")
    public ResponseEntity<ComparisonAnswerResponse> ask(@PathVariable UUID sessionId, @PathVariable UUID comparisonId,
                                                        @Valid @RequestBody ComparisonQuestionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(questionService.ask(sessionId, comparisonId, request.inputRevision(), request.question()));
    }
}
