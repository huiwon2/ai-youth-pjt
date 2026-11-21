package com.aiyouth.pjt.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.aiyouth.pjt.llm.guard.AnswerNumberGuard;
import com.aiyouth.pjt.llm.retriever.SourceExcerptRetrieverTestAccess;

class AnswerGuardAndKeywordTest {

    private static final List<String> SOURCE = List.of("월 최대 20만원을 최대 12개월 지원합니다. 신청기간: 2026.3.2~3.20");

    @Test
    void 근거에_있는_숫자만_쓴_답변은_통과() {
        assertThat(AnswerNumberGuard.numbersSupported("월 20만 원씩 12개월 받아요.", SOURCE)).isTrue();
        assertThat(AnswerNumberGuard.numbersSupported("공고문을 확인해 주세요.", SOURCE)).isTrue();
    }

    @Test
    void 근거에_없는_숫자가_있으면_실패() {
        assertThat(AnswerNumberGuard.numbersSupported("월 30만 원을 받아요.", SOURCE)).isFalse();
        assertThat(AnswerNumberGuard.numbersSupported("4월 1일부터 신청해요.", SOURCE)).isFalse();
    }

    @Test
    void 질문에서_조사와_불용어를_뺀_키워드() {
        assertThat(SourceExcerptRetrieverTestAccess.keywords("청년월세는 언제 신청하나요?"))
                .containsExactly("청년월세", "신청");
        assertThat(SourceExcerptRetrieverTestAccess.keywords("이 정책 뭐야")).isEmpty();
    }
}
