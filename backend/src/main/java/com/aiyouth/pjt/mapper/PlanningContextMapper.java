package com.aiyouth.pjt.mapper;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.aiyouth.pjt.model.enums.PriorityPreset;
import com.aiyouth.pjt.model.planning.DestinationRow;
import com.aiyouth.pjt.model.planning.HomeRow;
import com.aiyouth.pjt.model.planning.ProfileRow;
import com.aiyouth.pjt.model.planning.SessionRow;

/**
 * 백엔드 2가 계산에 쓰는 세션·프로필·목적지·후보 조회와 우선순위 저장(API 11).
 * 세션·프로필·후보의 생성/수정은 백엔드 1 담당이다.
 */
@Mapper
public interface PlanningContextMapper {

    SessionRow findSession(@Param("sessionId") UUID sessionId);

    ProfileRow findProfile(@Param("sessionId") UUID sessionId);

    DestinationRow findDestination(@Param("sessionId") UUID sessionId);

    List<HomeRow> findHomes(@Param("sessionId") UUID sessionId);

    /** expectedRevision이 맞을 때만 저장하고 revision을 1 올린다. 반영 행 수(0 또는 1)를 반환한다. */
    int updatePreferences(@Param("sessionId") UUID sessionId,
                          @Param("expectedRevision") int expectedRevision,
                          @Param("priorityPreset") PriorityPreset priorityPreset,
                          @Param("scoreWeightsJson") String scoreWeightsJson);
}
