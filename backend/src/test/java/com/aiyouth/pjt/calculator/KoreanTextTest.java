package com.aiyouth.pjt.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KoreanTextTest {

    @Test
    void 금액_표기() {
        assertThat(KoreanText.won(330_000)).isEqualTo("33만 원");
        assertThat(KoreanText.won(335_000)).isEqualTo("33만 5,000원");
        assertThat(KoreanText.won(7_000)).isEqualTo("7,000원");
        assertThat(KoreanText.won(0)).isEqualTo("0원");
        assertThat(KoreanText.won(52_000_000)).isEqualTo("5,200만 원");
    }

    @Test
    void 받침에_맞는_은는() {
        assertThat(KoreanText.topic("성수")).isEqualTo("성수는");
        assertThat(KoreanText.topic("신림")).isEqualTo("신림은");
        assertThat(KoreanText.topic("A")).isEqualTo("A은(는)");
    }

    @Test
    void 초를_분으로() {
        assertThat(KoreanText.minutes(1800)).isEqualTo(30);
        assertThat(KoreanText.minutes(780)).isEqualTo(13);
    }
}
