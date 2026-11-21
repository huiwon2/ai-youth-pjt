package com.aiyouth.pjt.dto.request.comparison;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** API 10 비교 생성 요청. */
public record ComparisonRequest(@NotNull @PositiveOrZero Integer inputRevision) {
}
