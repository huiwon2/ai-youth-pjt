package com.aiyouth.pjt.dto.request.comparison;

import com.aiyouth.pjt.dto.response.common.CommonTypes.Weights;
import com.aiyouth.pjt.model.enums.PriorityPreset;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * API 11 요청. 프리셋은 priorityPreset만, CUSTOM은 weights(각 0~100, 합 100)를 함께 보낸다.
 */
public record PreferencePatchRequest(@NotNull PriorityPreset priorityPreset, Weights weights) {

    @AssertTrue(message = "CUSTOM은 합계 100인 weights가 필요하고, 다른 프리셋은 weights를 보내지 않는다.")
    public boolean isWeightsConsistent() {
        if (priorityPreset == null) {
            return true;
        }
        if (priorityPreset == PriorityPreset.CUSTOM) {
            return weights != null && weights.isValid();
        }
        return weights == null;
    }

    public Weights resolvedWeights() {
        return priorityPreset == PriorityPreset.CUSTOM ? weights : Weights.of(priorityPreset);
    }
}
