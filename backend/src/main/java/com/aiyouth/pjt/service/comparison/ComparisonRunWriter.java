package com.aiyouth.pjt.service.comparison;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiyouth.pjt.global.revision.Revisions;
import com.aiyouth.pjt.mapper.ComparisonRunMapper;
import com.aiyouth.pjt.model.ComparisonRun;
import com.aiyouth.pjt.service.SessionState;

/**
 * 비교 결과 저장 트랜잭션. 입력은 트랜잭션 밖(ComparisonInputs.load)에서 읽었으므로,
 * 저장 직전에 세션 행을 잠그고 소유권과 revision을 다시 확인한다.
 */
@Service
public class ComparisonRunWriter {

    private final SessionState state;
    private final ComparisonRunMapper mapper;

    public ComparisonRunWriter(SessionState state, ComparisonRunMapper mapper) {
        this.state = state;
        this.mapper = mapper;
    }

    @Transactional
    public void save(UUID sessionId, String token, int inputRevision, ComparisonRun run) {
        Revisions.requireInput(inputRevision, state.lockOwned(sessionId, token).revision());
        mapper.insert(run);
    }
}
