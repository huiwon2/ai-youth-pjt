package com.aiyouth.pjt.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.Cost;

/** 기획서 7쪽 시연 표: 성수 67만·역삼 100만·신림 42만+관리비 별도, 월 20만 지원 적용 47만·80만·22만. */
class HomeCostCalculatorTest {

    @Test
    void 관리비를_아는_집은_월세와_관리비를_합하고_지원_기간_비용을_따로_낸다() {
        Cost seongsu = HomeCostCalculator.calculate(10_000_000, 600_000, 70_000L, 20_000_000L, 200_000L, null);
        Cost yeoksam = HomeCostCalculator.calculate(10_000_000, 900_000, 100_000L, 20_000_000L, 200_000L, null);

        assertThat(seongsu.monthlyHousingCostWon()).isEqualTo(670_000);
        assertThat(seongsu.monthlyAfterSupportWon()).isEqualTo(470_000);
        assertThat(yeoksam.monthlyHousingCostWon()).isEqualTo(1_000_000);
        assertThat(yeoksam.monthlyAfterSupportWon()).isEqualTo(800_000);
        assertThat(yeoksam.monthlyHousingCostWon() - seongsu.monthlyHousingCostWon()).isEqualTo(330_000);
    }

    @Test
    void 관리비_미확인은_0원으로_치지_않고_제외_표시한다() {
        Cost sillim = HomeCostCalculator.calculate(5_000_000, 420_000, null, 20_000_000L, 200_000L, null);

        assertThat(sillim.maintenanceWon()).isNull();
        assertThat(sillim.excludedUnknownMaintenance()).isTrue();
        assertThat(sillim.monthlyHousingCostWon()).isEqualTo(420_000);
        assertThat(sillim.monthlyAfterSupportWon()).isEqualTo(220_000);
    }

    @Test
    void 관리비_0원은_확인값이다() {
        Cost cost = HomeCostCalculator.calculate(0, 500_000, 0L, null, null, null);

        assertThat(cost.maintenanceWon()).isZero();
        assertThat(cost.excludedUnknownMaintenance()).isFalse();
        assertThat(cost.monthlyHousingCostWon()).isEqualTo(500_000);
    }

    @Test
    void 보증금_부족분과_참고_교통비() {
        Cost cost = HomeCostCalculator.calculate(30_000_000, 500_000, 50_000L, 20_000_000L, null, 1_550);

        assertThat(cost.depositShortfallWon()).isEqualTo(10_000_000);
        assertThat(cost.monthlyTransportFareWon()).isEqualTo(1_550L * 2 * 20);
        assertThat(cost.monthlyHousingCostWon()).isEqualTo(550_000); // 교통비는 주거비에 더하지 않는다
        assertThat(cost.monthlyAfterSupportWon()).isNull();
    }

    @Test
    void 모아둔_돈이_충분하거나_모르면() {
        assertThat(HomeCostCalculator.calculate(10_000_000, 1, null, 20_000_000L, null, null).depositShortfallWon())
                .isZero();
        assertThat(HomeCostCalculator.calculate(10_000_000, 1, null, null, null, null).depositShortfallWon()).isNull();
    }
}
