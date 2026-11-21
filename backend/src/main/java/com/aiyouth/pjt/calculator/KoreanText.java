package com.aiyouth.pjt.calculator;

import java.text.NumberFormat;
import java.util.Locale;

/** 답변 문장에 쓰는 금액·시간 표기. 계산값을 바꾸지 않고 표기만 만든다. */
public final class KoreanText {

    private KoreanText() {
    }

    /** 330000 → "33만 원", 335000 → "33만 5,000원", 7000 → "7,000원", 0 → "0원". */
    public static String won(long amount) {
        NumberFormat f = NumberFormat.getIntegerInstance(Locale.KOREA);
        long man = amount / 10_000;
        long rest = amount % 10_000;
        if (man == 0) {
            return f.format(rest) + "원";
        }
        return rest == 0 ? f.format(man) + "만 원" : f.format(man) + "만 " + f.format(rest) + "원";
    }

    /** 마지막 글자 받침에 맞춰 은/는을 붙인다. 한글이 아니면 "은(는)". */
    public static String topic(String word) {
        if (word == null || word.isEmpty()) {
            return "";
        }
        char last = word.charAt(word.length() - 1);
        if (last < '가' || last > '힣') {
            return word + "은(는)";
        }
        return word + (((last - '가') % 28) == 0 ? "는" : "은");
    }

    /** 초 → 분 (반올림). */
    public static int minutes(int seconds) {
        return Math.round(seconds / 60f);
    }
}
