package com.aiyouth.pjt.dto.response.comparison;

import java.util.UUID;

import com.aiyouth.pjt.dto.response.common.CommonTypes.Weights;
import com.aiyouth.pjt.model.enums.PriorityPreset;

/**
 * API 11 응답. 명세는 전체 Session을 반환하지만 Session 조립은 백엔드 1 담당이라,
 * 그 코드가 들어오기 전까지 우선순위 관련 필드만 반환한다.
 */
public record SessionPreferencesResponse(UUID sessionId, int revision, Preferences preferences) {

    public record Preferences(PriorityPreset priorityPreset, Weights weights) {
    }
}
