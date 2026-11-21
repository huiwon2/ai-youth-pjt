package com.aiyouth.pjt.model.planning;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.aiyouth.pjt.model.enums.PriorityPreset;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * planning_sessions 읽기 전용 뷰 (백엔드 2용).
 * 세션 생성·토큰 발급·프로필/후보 저장은 백엔드 1 담당이며, 해당 코드가 들어오면 그쪽 모델로 교체한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class SessionRow {
    private UUID id;
    private String accessTokenHash;
    private LocalDate assessmentDate;
    private int revision;
    private PriorityPreset priorityPreset;
    /** {"cost":40,"commute":35,"convenience":25} */
    private String scoreWeights;
    private OffsetDateTime expiresAt;
}
