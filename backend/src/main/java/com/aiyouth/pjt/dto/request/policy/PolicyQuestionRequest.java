package com.aiyouth.pjt.dto.request.policy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** API 6 공고문 질문 요청. */
public record PolicyQuestionRequest(
        @NotNull @PositiveOrZero Integer inputRevision,
        @NotBlank @Size(max = 500) String question,
        @Size(max = 200) String policyKey) {
}
