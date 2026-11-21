package com.aiyouth.pjt.dto.response.comparison;

import java.util.List;
import java.util.UUID;

import com.aiyouth.pjt.model.enums.AnswerSource;
import com.aiyouth.pjt.model.enums.AnswerStatus;

/** API 12 응답 (ComparisonAnswer). references는 답변에 쓴 비교 결과 필드다. */
public record ComparisonAnswerResponse(AnswerStatus status, String answer, AnswerSource answerSource,
                                       List<Reference> references, UUID comparisonId, int inputRevision) {

    public record Reference(UUID homeId, String field, Object value) {
    }
}
