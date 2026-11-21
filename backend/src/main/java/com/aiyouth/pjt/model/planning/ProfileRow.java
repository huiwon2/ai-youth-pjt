package com.aiyouth.pjt.model.planning;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * profiles 읽기 전용 뷰. 출생연도·생일 답변·소득 주기·입주·부모 주소 답변은
 * additional_answers(JSON)에 있다 (API 명세 「ERD 매핑」).
 */
@Getter
@Setter
@NoArgsConstructor
public class ProfileRow {
    private Integer ageYears;
    private LocalDate ageAsOfDate;
    private String employmentStatus;
    private Long availableCashWon;
    private Long monthlyIncomeWon;
    private Boolean isHomeless;
    private Integer maxCommuteMinutes;
    private String additionalAnswers;
    private OffsetDateTime confirmedAt;
}
