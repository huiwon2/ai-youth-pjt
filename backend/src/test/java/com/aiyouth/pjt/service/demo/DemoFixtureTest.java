package com.aiyouth.pjt.service.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.aiyouth.pjt.dto.response.policy.PolicyEvaluationResponse;
import com.aiyouth.pjt.dto.response.policy.PolicyEvaluationResponse.PolicyItem;

import tools.jackson.databind.json.JsonMapper;

/** 시연 픽스처가 기획서 숫자(27세 520만 원, 35세 280만 원)와 내부적으로 맞는지 확인한다. */
class DemoFixtureTest {

    private final DemoScenarios scenarios = new DemoScenarios(true, new JsonMapper());
    private final DemoFixture fixture = readFixture();

    private static DemoFixture readFixture() {
        try (var in = DemoFixtureTest.class.getResourceAsStream("/demo/demo-fixture.json")) {
            return new JsonMapper().readValue(in, DemoFixture.class);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void 합계는_포함된_정책의_상한을_더한_값이다() {
        for (DemoFixture.Evaluation e : fixture.evaluations()) {
            Map<String, PolicyItem> byKey = e.policies().stream()
                    .collect(Collectors.toMap(PolicyItem::policyKey, p -> p));
            long sum = e.includedPolicyKeys().stream()
                    .mapToLong(k -> byKey.get(k).benefit().potentialTotalWon()).sum();
            assertThat(sum).as("age %d", e.ageYears()).isEqualTo(e.potentialTotalWon());
        }
        assertThat(fixture.evaluations()).extracting(DemoFixture.Evaluation::potentialTotalWon)
                .containsExactly(5_200_000L, 2_800_000L);
    }

    @Test
    void 픽스처를_읽고_버전으로_시연을_구분한다() {
        assertThat(scenarios.version()).startsWith("demo:");
        assertThat(fixture.comparison().homes()).hasSize(3);
    }

    @Test
    void 가정_차이는_추가_제외_정책을_구분한다() {
        PolicyEvaluationResponse base = response(fixture.evaluations().get(0));
        PolicyEvaluationResponse scenario = response(fixture.evaluations().get(1));
        PolicyEvaluationResponse.Delta delta = com.aiyouth.pjt.service.policy.PolicyEvaluationServiceTestAccess
                .delta(base, scenario);

        assertThat(delta.baseTotalWon()).isEqualTo(5_200_000L);
        assertThat(delta.scenarioTotalWon()).isEqualTo(2_800_000L);
        assertThat(delta.addedPolicyKeys()).containsExactly("DEMO-SEOUL-YOUTH-RENT");
        assertThat(delta.removedPolicyKeys()).containsExactly("DEMO-MOLIT-YOUTH-RENT");
    }

    private static PolicyEvaluationResponse response(DemoFixture.Evaluation e) {
        return new PolicyEvaluationResponse(1, java.time.LocalDate.of(2026, 11, 21), "demo", new PolicyEvaluationResponse.Summary(
                e.potentialTotalWon(), e.totalStatus(), e.includedPolicyKeys(), 2026), e.policies(),
                java.util.List.of(), java.util.List.of(), java.util.List.of(), e.conflicts(), null);
    }
}
