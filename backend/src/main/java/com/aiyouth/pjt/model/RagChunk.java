package com.aiyouth.pjt.model;

import java.util.UUID;

import com.aiyouth.pjt.model.enums.VerificationStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * rag_chunks 한 행 = 공고문 검색 문단. 청크 생성·임베딩·검색은 Python 서비스가 담당하고,
 * Java는 근거 확인과 원문 발췌 대체 답변에만 읽는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class RagChunk {
    private UUID id;
    /** 정책 미연결 원문이면 null. */
    private UUID policyId;
    /** 정책에 연결된 청크는 policies.policy_key와 같다. */
    private String sourceKey;
    private String documentKey;
    private String documentRevision;
    private int chunkIndex;
    private String section;
    private String content;
    private String sourceUrl;
    private String locator;
    private String contentHash;
    private VerificationStatus verificationStatus;
    /** is_active. SQL에서 active로 별칭을 준다 (Lombok boolean 접근자 이름과 맞춤). */
    private boolean active;
    /** 조인으로 읽은 정책명 (근거 제목 표시용). */
    private String policyName;
}
