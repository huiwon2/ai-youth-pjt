package com.aiyouth.pjt.llm.guard;

import java.util.regex.Pattern;

/**
 * AI 답변을 화면용 평문으로 바꾼다. 프론트는 답변을 문자열 그대로 출력하므로
 * 마크다운 기호(제목 #, 굵게 **, 인용 >)와 RAG 내부 출처 번호([4])를 지운다.
 * 원문 문장·숫자·줄바꿈은 바꾸지 않는다.
 */
public final class AnswerText {

    private static final Pattern HEADING = Pattern.compile("(?m)^[ \\t]{0,3}#{1,6}[ \\t]+");
    private static final Pattern QUOTE = Pattern.compile("(?m)^[ \\t]*>[ \\t]?");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern SOURCE_NUMBER = Pattern.compile("[ \\t]*\\[\\d{1,3}]");
    private static final Pattern TRAILING_SPACE = Pattern.compile("(?m)[ \\t]+$");
    private static final Pattern EXTRA_BLANK_LINES = Pattern.compile("\\n{3,}");

    private AnswerText() {
    }

    public static String toPlain(String text) {
        if (text == null) {
            return null;
        }
        String plain = text.replace("\r\n", "\n");
        plain = HEADING.matcher(plain).replaceAll("");
        plain = QUOTE.matcher(plain).replaceAll("");
        plain = BOLD.matcher(plain).replaceAll("$1");
        plain = SOURCE_NUMBER.matcher(plain).replaceAll("");
        plain = TRAILING_SPACE.matcher(plain).replaceAll("");
        plain = EXTRA_BLANK_LINES.matcher(plain).replaceAll("\n\n");
        return plain.strip();
    }
}
