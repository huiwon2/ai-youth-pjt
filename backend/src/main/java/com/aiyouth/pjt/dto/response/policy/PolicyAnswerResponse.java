package com.aiyouth.pjt.dto.response.policy;

import java.time.LocalDate;
import java.util.List;

import com.aiyouth.pjt.model.enums.AnswerSource;
import com.aiyouth.pjt.model.enums.AnswerStatus;

/** API 6 응답 (PolicyAnswer). */
public record PolicyAnswerResponse(AnswerStatus status, String answer, AnswerSource answerSource,
                                   List<Evidence> evidence, List<HelpLink> helpLinks, int inputRevision,
                                   LocalDate assessmentDate) {

    public record HelpLink(String label, String url) {
    }
}
