package com.aiyouth.pjt.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.aiyouth.pjt.model.enums.ComparisonStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * comparison_runs 한 행 = 비교 계산 당시의 입력·정책·결과 스냅샷 (API 10).
 * GET은 다시 계산하지 않고 result_snapshot을 그대로 읽는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class ComparisonRun {
    private UUID id;
    private UUID sessionId;
    private int inputRevision;
    private LocalDate assessmentDate;
    private String calculationVersion;
    private ComparisonStatus status;
    /** 프로필·목적지·후보·가중치·scenario_type (jsonb 객체). */
    private String inputSnapshot;
    /** [{policy: 14개 필드, catalog_hash}] (jsonb 배열). */
    private String policySnapshot;
    /** 응답 Comparison 본문 (isCurrent 제외, jsonb 객체). */
    private String resultSnapshot;
    private OffsetDateTime calculatedAt;
}
