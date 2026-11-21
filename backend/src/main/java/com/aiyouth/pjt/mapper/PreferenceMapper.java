package com.aiyouth.pjt.mapper;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.aiyouth.pjt.model.PlanningRows.SessionRow;

/** API 11 우선순위 저장. 백엔드 1의 PlanningMapper.lockOwnedSession과 같은 컬럼 순서로 갱신된 행을 돌려준다. */
@Mapper
public interface PreferenceMapper {

    /** 우선순위·가중치 저장과 revision +1을 한 문장으로 처리하고 갱신된 행을 반환한다. */
    @Select(value = """
            UPDATE planning_sessions
            SET priority_preset = #{preset}, score_weights = CAST(#{weights} AS jsonb),
                revision = revision + 1, updated_at = #{now}
            WHERE id = #{id}
            RETURNING id, assessment_date, revision, priority_preset, score_weights::text, expires_at
            """, affectData = true)
    @Options(flushCache = Options.FlushCachePolicy.TRUE)
    SessionRow updatePreferences(@Param("id") UUID id, @Param("preset") String preset,
                                 @Param("weights") String weights, @Param("now") OffsetDateTime now);
}
