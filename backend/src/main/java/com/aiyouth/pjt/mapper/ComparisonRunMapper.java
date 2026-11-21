package com.aiyouth.pjt.mapper;

import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.aiyouth.pjt.model.ComparisonRun;

@Mapper
public interface ComparisonRunMapper {

    void insert(ComparisonRun run);

    /** 다른 세션의 비교 ID는 조회되지 않는다. */
    ComparisonRun findByIdAndSession(@Param("id") UUID id, @Param("sessionId") UUID sessionId);
}
