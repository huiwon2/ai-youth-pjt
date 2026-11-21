package com.aiyouth.pjt.dto.request.comparison;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** API 12 비교 후속 질문 요청. */
public record ComparisonQuestionRequest(
        @NotNull @PositiveOrZero Integer inputRevision,
        @NotBlank @Size(max = 500) String question) {
}
