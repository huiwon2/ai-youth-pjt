package com.aiyouth.pjt.model;

import java.util.UUID;

import com.aiyouth.pjt.model.enums.PolicySource;
import com.aiyouth.pjt.model.enums.ReviewDecision;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * policies 한 행 = policy_catalog.v3 카탈로그 한 건 (14개 필드).
 * benefit·application·eligibility는 jsonb 원문(JSON 문자열) 그대로 보관한다. 내부 null은 미확인이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Policy {
    /** UUIDv5(NAMESPACE_URL, "first-home/policy/" + policyKey). 정제 결과의 값을 보존한다. */
    private UUID id;
    private String policyKey;
    private PolicySource source;
    private String externalId;
    private String name;
    private ReviewDecision reviewDecision;
    private String organization;
    private String serviceField;
    private String summary;
    private String benefit;
    private String application;
    private String eligibility;
    private String sourceUrl;
    /** 원본 수정시각 문자열. 날짜 타입으로 강제 변환하지 않는다. */
    private String sourceModifiedAtRaw;
}
