package com.aiyouth.pjt.calculator;

import java.time.LocalDate;

/**
 * 출생연도와 생일 답변으로 판정일의 만 나이 범위를 구한다 (API 명세 3번 「생일 답변과 만 나이」).
 * 같은 해 이후 날짜에는 이전 '생일 지남(true)'만 유효하고, '생일 전(false)'·다른 연도·미래 기준일 답변은 미확인이다.
 */
public final class AgeCalculator {

    private AgeCalculator() {
    }

    public record AgeRange(int minYears, int maxYears, Integer exactYears) {
        public boolean isExact() {
            return exactYears != null;
        }
    }

    /**
     * @param birthYear              출생연도. null이면 null 반환
     * @param birthdayPassed         저장된 생일 답변 (null = 미응답)
     * @param answerAsOfDate         답변 기준일 (null = 미응답)
     * @param assessmentDate         판정일
     */
    public static AgeRange ageRange(Integer birthYear, Boolean birthdayPassed, LocalDate answerAsOfDate,
                                    LocalDate assessmentDate) {
        if (birthYear == null || birthYear > assessmentDate.getYear()) {
            return null;
        }
        int passedAge = assessmentDate.getYear() - birthYear;
        int beforeAge = Math.max(0, passedAge - 1);
        Boolean effective = effectiveAnswer(birthdayPassed, answerAsOfDate, assessmentDate);
        if (effective == null) {
            return new AgeRange(beforeAge, passedAge, null);
        }
        int exact = effective ? passedAge : beforeAge;
        return new AgeRange(exact, exact, exact);
    }

    /** 판정일에 유효한 생일 답변. 유효하지 않으면 null. */
    public static Boolean effectiveAnswer(Boolean birthdayPassed, LocalDate answerAsOfDate, LocalDate assessmentDate) {
        if (birthdayPassed == null || answerAsOfDate == null) {
            return null;
        }
        if (answerAsOfDate.getYear() != assessmentDate.getYear() || answerAsOfDate.isAfter(assessmentDate)) {
            return null;
        }
        if (answerAsOfDate.isEqual(assessmentDate)) {
            return birthdayPassed;
        }
        return birthdayPassed ? Boolean.TRUE : null;
    }
}
