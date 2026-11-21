package com.aiyouth.pjt.llm.retriever;

import java.util.List;

/** 패키지 전용 메서드를 다른 테스트 패키지에서 쓰기 위한 통로. */
public final class SourceExcerptRetrieverTestAccess {

    private SourceExcerptRetrieverTestAccess() {
    }

    public static List<String> keywords(String question) {
        return SourceExcerptRetriever.keywords(question);
    }
}
