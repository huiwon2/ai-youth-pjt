package com.aiyouth.pjt.dto.request.policy;

import com.aiyouth.pjt.model.enums.EmploymentStatus;
import com.aiyouth.pjt.model.enums.HousingType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** API 5 가정 계산 요청. overrides는 저장된 프로필을 바꾸지 않는다. */
public record PolicyEvaluationRequest(
        @NotNull @PositiveOrZero Integer inputRevision,
        @Valid Overrides overrides) {

    public record Overrides(
            @PositiveOrZero @Max(120) Integer ageYears,
            @PositiveOrZero Long monthlyIncomeWon,
            Boolean ownsHome,
            EmploymentStatus employmentStatus,
            HousingType housingType) {

        public boolean isEmpty() {
            return ageYears == null && monthlyIncomeWon == null && ownsHome == null && employmentStatus == null
                    && housingType == null;
        }
    }
}
