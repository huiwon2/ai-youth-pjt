package com.aiyouth.pjt.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.aiyouth.pjt.llm.guard.AnswerText;

/** RAG(PR #7) 평가 기록의 실제 답변 형식으로 확인한다. */
class AnswerTextTest {

    private static final String RAG_ANSWER = """
            ### 북한이탈주민 정착금 지원(가산금) [4]

            **지원 내용 원문**
            > ○ 고령가산금 : 보호 결정 당시 만 60세 이상인 북한이탈주민에게 지원\s
            >  - 분기별 50만원씩, 16회 지급으로 총 800만 원
            >   \s


            **지원 대상 원문**
            > ○ 북한이탈주민의 보호 및 정착 지원에 관한 법률 제8조 제1항에 따른 보호 결정을 받은 북한이탈주민

            원본 자료에 대한 검토가 필요하며, 최종 자격과 최신 신청 가능 여부는 공고 원문에서 확인해 주세요.""";

    @Test
    void 마크다운_기호와_출처_번호를_지우고_원문은_그대로_둔다() {
        String plain = AnswerText.toPlain(RAG_ANSWER);

        assertThat(plain).doesNotContain("###", "**", "> ", "[4]");
        assertThat(plain).startsWith("북한이탈주민 정착금 지원(가산금)\n\n지원 내용 원문\n○ 고령가산금");
        assertThat(plain).contains(" - 분기별 50만원씩, 16회 지급으로 총 800만 원");
        assertThat(plain).contains("지원 대상 원문\n○ 북한이탈주민의 보호");
        assertThat(plain).doesNotContain("\n\n\n");
        assertThat(plain).endsWith("공고 원문에서 확인해 주세요.");
    }

    @Test
    void 평문은_바뀌지_않는다() {
        assertThat(AnswerText.toPlain("월 20만 원씩 12개월 받을 수 있어요.")).isEqualTo("월 20만 원씩 12개월 받을 수 있어요.");
        assertThat(AnswerText.toPlain(null)).isNull();
    }
}
