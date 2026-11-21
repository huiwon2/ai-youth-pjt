package com.aiyouth.pjt.llm.guard;

import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 답변의 숫자가 근거에 있는지 확인한다.
 * 출처에 없는 수치·날짜를 만들지 않도록, 근거 원문에 없는 숫자가 하나라도 있으면 실패로 본다.
 */
public final class AnswerNumberGuard {

    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");

    private AnswerNumberGuard() {
    }

    /** answer 속 숫자가 모두 sources 중 어딘가에 있으면 true. 숫자가 없으면 true. */
    public static boolean numbersSupported(String answer, Collection<String> sources) {
        String haystack = normalize(String.join("\n", sources));
        for (String number : numbers(answer)) {
            if (!haystack.contains(number)) {
                return false;
            }
        }
        return true;
    }

    static List<String> numbers(String text) {
        Matcher m = NUMBER.matcher(text == null ? "" : text);
        return m.results().map(r -> normalize(r.group())).toList();
    }

    private static String normalize(String text) {
        return text.replace(",", "");
    }
}
