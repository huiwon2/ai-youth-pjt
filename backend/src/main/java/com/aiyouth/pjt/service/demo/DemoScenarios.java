package com.aiyouth.pjt.service.demo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.aiyouth.pjt.dto.request.policy.PolicyEvaluationRequest.Overrides;
import com.aiyouth.pjt.dto.response.PlanningResponse.Home;
import com.aiyouth.pjt.dto.response.PlanningResponse.Profile;

import tools.jackson.databind.json.JsonMapper;

/**
 * 승인된 시연 데이터 선택.
 * firsthome.demo.enabled=true이고 저장된 프로필이 시연 조건과 정확히 같을 때만 픽스처를 쓴다.
 * 그 밖의 새 입력은 검증된 규칙이 없으므로 CHECK_NEEDED/UNKNOWN으로 판정한다.
 */
@Component
public class DemoScenarios {

    private final boolean enabled;
    private final DemoFixture fixture;

    public DemoScenarios(@Value("${firsthome.demo.enabled:false}") boolean enabled, JsonMapper json) {
        this.enabled = enabled;
        try (InputStream in = new ClassPathResource("demo/demo-fixture.json").getInputStream()) {
            this.fixture = json.readValue(in, DemoFixture.class);
        } catch (IOException e) {
            throw new UncheckedIOException("demo/demo-fixture.json을 읽지 못했어요.", e);
        }
    }

    public String version() {
        return fixture.version();
    }

    /** 시연 프로필(만 27세·월 230만·보증금 2,000만·멀티캠퍼스)과 일치하는지. 나이는 생일 답변까지 확정된 경우만. */
    public boolean matchesProfile(Profile profile) {
        if (!enabled || profile == null || profile.age() == null || profile.age().exactYears() == null) {
            return false;
        }
        DemoFixture.Condition c = fixture.condition();
        return profile.age().exactYears() == c.ageYears()
                && Objects.equals(profile.normalizedMonthlyIncomeWon(), c.monthlyIncomeWon())
                && Objects.equals(profile.availableCashWon(), c.availableCashWon())
                && profile.frequentPlace() != null && profile.frequentPlace().name() != null
                && profile.frequentPlace().name().contains(c.destinationKeyword());
    }

    /** 현재 조건의 시연 판정 (API 4). */
    public Optional<DemoFixture.Evaluation> baseEvaluation(Profile profile) {
        return matchesProfile(profile) ? evaluationForAge(fixture.condition().ageYears()) : Optional.empty();
    }

    /** 가정 조건의 시연 판정 (API 5). 나이만 바꾼 가정이고 그 나이의 승인된 값이 있을 때만 반환한다. */
    public Optional<DemoFixture.Evaluation> scenarioEvaluation(Profile profile, Overrides overrides) {
        if (!matchesProfile(profile) || overrides == null || overrides.ageYears() == null) {
            return Optional.empty();
        }
        boolean onlyAge = overrides.monthlyIncomeWon() == null && overrides.ownsHome() == null
                && overrides.employmentStatus() == null && overrides.housingType() == null;
        return onlyAge ? evaluationForAge(overrides.ageYears()) : Optional.empty();
    }

    /** 시연 후보 3곳(성수·역삼·신림의 월세·관리비)과 슬롯별로 일치하면 비교용 시연 값을 반환한다. */
    public Optional<DemoFixture.Comparison> comparison(Profile profile, List<Home> homes) {
        if (!matchesProfile(profile)) {
            return Optional.empty();
        }
        List<DemoFixture.Home> expected = fixture.comparison().homes();
        if (homes.size() != expected.size()) {
            return Optional.empty();
        }
        for (Home home : homes) {
            boolean found = expected.stream().anyMatch(e -> e.slot() == home.slot()
                    && e.monthlyRentWon() == home.monthlyRentWon()
                    && Objects.equals(e.maintenanceWon(), home.maintenanceWon()));
            if (!found) {
                return Optional.empty();
            }
        }
        return Optional.of(fixture.comparison());
    }

    private Optional<DemoFixture.Evaluation> evaluationForAge(int ageYears) {
        return fixture.evaluations().stream().filter(e -> e.ageYears() == ageYears).findFirst();
    }
}
