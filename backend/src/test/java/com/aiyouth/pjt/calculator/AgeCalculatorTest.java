package com.aiyouth.pjt.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.aiyouth.pjt.calculator.AgeCalculator.AgeRange;

/** 프로젝트 명세 6절: 2026-11-19 기준 1991년생. */
class AgeCalculatorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 19);

    @Test
    void 생일_지남이면_35세_생일_전이면_34세_미응답이면_34에서_35세() {
        assertThat(AgeCalculator.ageRange(1991, true, DAY, DAY)).isEqualTo(new AgeRange(35, 35, 35));
        assertThat(AgeCalculator.ageRange(1991, false, DAY, DAY)).isEqualTo(new AgeRange(34, 34, 34));
        assertThat(AgeCalculator.ageRange(1991, null, null, DAY)).isEqualTo(new AgeRange(34, 35, null));
    }

    @Test
    void 같은_해_다음날_복원하면_생일_지남만_유지한다() {
        LocalDate next = DAY.plusDays(1);
        assertThat(AgeCalculator.ageRange(1991, true, DAY, next).exactYears()).isEqualTo(35);
        assertThat(AgeCalculator.ageRange(1991, false, DAY, next)).isEqualTo(new AgeRange(34, 35, null));
    }

    @Test
    void 다른_해나_미래_기준일의_답변은_미확인() {
        assertThat(AgeCalculator.ageRange(1991, true, LocalDate.of(2025, 12, 1), DAY).isExact()).isFalse();
        assertThat(AgeCalculator.ageRange(1991, true, DAY.plusDays(3), DAY).isExact()).isFalse();
    }

    @Test
    void 출생연도가_없으면_null() {
        assertThat(AgeCalculator.ageRange(null, true, DAY, DAY)).isNull();
    }
}
