package com.aiyouth.pjt.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.aiyouth.pjt.model.Policy;
import com.aiyouth.pjt.model.enums.ReviewDecision;

/** policies 조회. 적재(upsert)는 카탈로그 파일이 준비되면 별도 로더로 추가한다. */
@Mapper
public interface PolicyMapper {

    /** 서비스 관련성이 결정된 정책 목록. INCLUDE는 개인 자격 판정이 아니다. */
    List<Policy> findByReviewDecision(@Param("decision") ReviewDecision decision);

    Policy findByPolicyKey(@Param("policyKey") String policyKey);
}
