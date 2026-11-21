package com.aiyouth.pjt.calculator;

import com.aiyouth.pjt.dto.response.comparison.ComparisonResponse.Cost;

/**
 * 후보 집의 비용 계산 (프로젝트 명세 3.2).
 * <ul>
 *   <li>한 달 비용 = 월세 + 확인된 관리비. 관리비 미확인(null)은 0원으로 처리하지 않고 월세만 넣은 뒤 제외 표시.</li>
 *   <li>보증금 부족분 = max(0, 보증금 − 모아둔 돈). 모아둔 돈을 모르면 null.</li>
 *   <li>지원 적용 예시 = 한 달 비용 − 지급 기간의 월 지원액. 월 지원액을 모르면 null. 일회성 지원은 빼지 않는다.</li>
 *   <li>참고 교통비 = 편도 요금 × 2 × 20일. 요금을 모르면 null. 주거비 합계에는 더하지 않는다.</li>
 * </ul>
 */
public final class HomeCostCalculator {

    static final int COMMUTE_DAYS_PER_MONTH = 20;

    private HomeCostCalculator() {
    }

    public static Cost calculate(long depositWon, long monthlyRentWon, Long maintenanceWon, Long availableCashWon,
                                 Long monthlySupportWon, Integer oneWayFareWon) {
        boolean maintenanceUnknown = maintenanceWon == null;
        long monthlyHousingCost = monthlyRentWon + (maintenanceUnknown ? 0 : maintenanceWon);
        Long shortfall = availableCashWon == null ? null : Math.max(0, depositWon - availableCashWon);
        Long afterSupport = monthlySupportWon == null ? null : Math.max(0, monthlyHousingCost - monthlySupportWon);
        Long transport = oneWayFareWon == null ? null : (long) oneWayFareWon * 2 * COMMUTE_DAYS_PER_MONTH;
        return new Cost(depositWon, shortfall, monthlyRentWon, maintenanceWon, monthlyHousingCost, maintenanceUnknown,
                afterSupport, transport);
    }
}
