package com.aiyouth.pjt.service.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aiyouth.pjt.calculator.HomeCostCalculator;
import com.aiyouth.pjt.dto.response.PlanningResponse.Route;
import com.aiyouth.pjt.dto.response.PlanningResponse.Weights;
import com.aiyouth.pjt.dto.response.comparison.ComparisonAnswerResponse;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.ComparisonHome;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.Recommendation;
import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.Scores;
import com.aiyouth.pjt.model.enums.AnswerSource;
import com.aiyouth.pjt.model.enums.AnswerStatus;
import com.aiyouth.pjt.model.enums.ComparisonStatus;
import com.aiyouth.pjt.model.enums.PriorityPreset;

/** 기획서 8쪽: ‘역삼이랑 성수 차이는?’ → 월 33만 원, 편도 17분. */
class ComparisonAnswerTest {

    private static final Instant NOW = Instant.parse("2026-11-21T01:00:00Z");
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    private static ComparisonHome home(UUID id, int slot, String label, long rent, Long maintenance, int seconds,
                                       Integer score, Integer rank) {
        Route route = new Route("OK", "TRANSIT", "TEST", seconds, null, null, null, List.of(), null, NOW);
        return new ComparisonHome(id, slot, label, null,
                HomeCostCalculator.calculate(10_000_000, rent, maintenance, 20_000_000L, null, null), route, List.of(),
                List.of(), new Scores(null, null, null, score), rank);
    }

    private static ComparisonResponse demo() {
        List<ComparisonHome> homes = List.of(
                home(A, 1, "성수동 원룸", 600_000, 70_000L, 1800, 87, 1),
                home(B, 2, "역삼동 오피스텔", 900_000, 100_000L, 780, 77, 2),
                home(C, 3, "신림동 원룸", 420_000, null, 1800, null, null));
        return new ComparisonResponse(UUID.randomUUID(), 5, LocalDate.of(2026, 11, 21), "demo:2026-11-19",
                ComparisonStatus.PARTIAL, true, PriorityPreset.BALANCED,
                new Weights(BigDecimal.valueOf(40), BigDecimal.valueOf(35), BigDecimal.valueOf(25)), homes,
                ComparisonService.recommend(homes), List.of(), NOW);
    }

    @Test
    void 두_집_차이는_비교표의_월_비용과_편도_시간으로_답한다() {
        ComparisonAnswerResponse answer = ComparisonQuestionService.answer(demo(), "역삼이랑 성수 차이는?");

        assertThat(answer.status()).isEqualTo(AnswerStatus.ANSWERED);
        assertThat(answer.answerSource()).isEqualTo(AnswerSource.RULE);
        assertThat(answer.answer()).contains("성수동 원룸은 역삼동 오피스텔보다 한 달 33만 원 적게 들어요")
                .contains("역삼동 오피스텔은 편도 17분 짧아요");
        assertThat(answer.references()).extracting(ComparisonAnswerResponse.Reference::field)
                .contains("cost.monthlyHousingCostWon", "route.durationSeconds");
    }

    @Test
    void 관리비_미확인_집과의_비용_차이는_계산하지_않는다() {
        ComparisonAnswerResponse answer = ComparisonQuestionService.answer(demo(), "신림이랑 성수 비교해줘");

        assertThat(answer.answer()).contains("신림동 원룸은 관리비를 몰라").doesNotContain("만 원 적게");
    }

    @Test
    void 추천_이유는_저장된_순위와_차이에서_만든다() {
        ComparisonResponse demo = demo();
        Recommendation rec = demo.recommendation();

        assertThat(rec.homeId()).isEqualTo(A);
        assertThat(rec.rankedHomeIds()).containsExactly(A, B);
        assertThat(rec.reasons()).containsExactly("역삼동 오피스텔보다 한 달 33만 원 적게 들어요.");
        assertThat(rec.tradeoffs()).contains("신림동 원룸은 관리비를 확인하지 못해 순위에서 보류했어요.",
                "역삼동 오피스텔보다 편도 17분 더 걸려요.");
        assertThat(ComparisonQuestionService.answer(demo, "왜 이 집을 추천했어?").answer()).startsWith("성수동 원룸은 1위예요.");
    }

    @Test
    void 정책_신청_질문과_비교에_없는_정보는_범위_밖() {
        assertThat(ComparisonQuestionService.answer(demo(), "청년월세는 언제 신청해?").status())
                .isEqualTo(AnswerStatus.OUT_OF_SCOPE);
        assertThat(ComparisonQuestionService.answer(demo(), "성수동 집 소음은 어때?").status())
                .isEqualTo(AnswerStatus.OUT_OF_SCOPE);
    }

    @Test
    void 한_집_질문과_알_수_없는_질문() {
        assertThat(ComparisonQuestionService.answer(demo(), "성수동 집의 월 비용은 얼마야?").answer())
                .isEqualTo("성수동 원룸은 한 달 67만 원이에요. 편도 30분 걸려요.");
        assertThat(ComparisonQuestionService.answer(demo(), "날씨 어때?").status()).isEqualTo(AnswerStatus.NO_EVIDENCE);
    }

    @Test
    void 순위가_없으면_추천을_보류한다() {
        List<ComparisonHome> homes = List.of(home(A, 1, "가", 500_000, null, 1800, null, null),
                home(B, 2, "나", 600_000, null, 1800, null, null));
        Recommendation rec = ComparisonService.recommend(homes);

        assertThat(rec.homeId()).isNull();
        assertThat(rec.rankedHomeIds()).isEmpty();
        assertThat(rec.tradeoffs()).hasSize(2);
    }

    @Test
    void 주소가_라벨이어도_동_이름으로_집을_찾는다() {
        assertThat(ComparisonQuestionService.labelTokens("서울 성동구 성수동2가 333-76")).containsExactly("성수동", "성수");
        assertThat(ComparisonQuestionService.labelTokens("역삼동 오피스텔")).containsExactly("역삼동", "역삼");
        assertThat(ComparisonQuestionService.labelTokens("서울 강남구 역삼동 778-26")).containsExactly("역삼동", "역삼");

        List<ComparisonHome> homes = List.of(
                home(A, 1, "서울 성동구 성수동2가 333-76", 600_000, 70_000L, 1800, 87, 1),
                home(B, 2, "서울 강남구 역삼동 778-26", 900_000, 100_000L, 780, 77, 2));
        ComparisonResponse comparison = new ComparisonResponse(UUID.randomUUID(), 5, LocalDate.of(2026, 11, 21),
                "demo:2026-11-19", ComparisonStatus.COMPLETE, true, PriorityPreset.BALANCED,
                new Weights(BigDecimal.valueOf(40), BigDecimal.valueOf(35), BigDecimal.valueOf(25)), homes,
                ComparisonService.recommend(homes), List.of(), NOW);

        assertThat(ComparisonQuestionService.answer(comparison, "역삼이랑 성수 차이는?").answer())
                .contains("한 달 33만 원 적게 들어요").contains("편도 17분 짧아요");
    }
}
