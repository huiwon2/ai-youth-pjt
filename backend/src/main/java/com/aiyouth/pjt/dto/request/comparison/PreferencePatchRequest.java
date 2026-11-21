package com.aiyouth.pjt.dto.request.comparison;

import com.aiyouth.pjt.model.enums.PriorityPreset;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * API 11 요청. 프리셋은 priorityPreset만, CUSTOM은 weights(각 0~100, 합 100)를 함께 보낸다.
 */
public record PreferencePatchRequest(@NotNull PriorityPreset priorityPreset, Weights weights) {

    public record Weights(int cost, int commute, int convenience) {
        public static Weights of(PriorityPreset preset) {
            return new Weights(preset.cost(), preset.commute(), preset.convenience());
        }

        @JsonIgnore
        public boolean isValid() {
            return cost >= 0 && cost <= 100 && commute >= 0 && commute <= 100
                    && convenience >= 0 && convenience <= 100 && cost + commute + convenience == 100;
        }
    }

    @AssertTrue(message = "CUSTOM은 합계 100인 weights가 필요하고, 다른 프리셋은 weights를 보내지 않는다.")
    @JsonIgnore
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
