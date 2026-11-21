package com.aiyouth.pjt.mapper;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.aiyouth.pjt.model.RagChunk;

/**
 * rag_chunks 조회. 모든 메서드는 VERIFIED·활성 문단만 돌려준다.
 * 의미 검색은 Python 서비스가 하고, 여기서는 근거 확인과 원문 발췌 대체 답변에만 쓴다.
 */
@Mapper
public interface RagChunkMapper {

    /** Python이 돌려준 근거 ID 중 실제로 검증된 문단만 남긴다. */
    List<RagChunk> findVerifiedByIds(@Param("ids") Collection<UUID> ids);

    /** 정책들의 근거 문단 (지원 조회의 evidence). 정책별 개수 제한은 서비스에서 한다. */
    List<RagChunk> findVerifiedBySourceKeys(@Param("sourceKeys") Collection<String> sourceKeys);

    /** Python을 쓸 수 없을 때의 키워드 원문 검색. policyKey가 있으면 그 정책으로 좁힌다. */
    List<RagChunk> searchVerifiedByKeywords(@Param("policyKey") String policyKey,
                                            @Param("keywords") List<String> keywords,
                                            @Param("limit") int limit);
}
