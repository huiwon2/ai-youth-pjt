package com.aiyouth.pjt.service.comparison;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiyouth.pjt.dto.request.comparison.PreferencePatchRequest;
import com.aiyouth.pjt.dto.response.common.CommonTypes.Weights;
import com.aiyouth.pjt.dto.response.comparison.SessionPreferencesResponse;
import com.aiyouth.pjt.dto.response.comparison.SessionPreferencesResponse.Preferences;
import com.aiyouth.pjt.global.exception.ApiException;
import com.aiyouth.pjt.global.exception.ErrorCode;
import com.aiyouth.pjt.mapper.PlanningContextMapper;
import com.aiyouth.pjt.model.planning.SessionRow;
import com.aiyouth.pjt.service.planning.PlanningContextReader;

import tools.jackson.databind.json.JsonMapper;

/**
 * API 11 우선순위 저장. 저장과 revision 증가를 한 UPDATE(한 트랜잭션)로 처리하고,
 * If-Match의 revision이 다르면 412로 거절한다. 값이 그대로면 revision을 올리지 않는다.
 */
@Service
public class PreferenceService {

    private final PlanningContextReader reader;
    private final PlanningContextMapper mapper;
    private final JsonMapper json;

    public PreferenceService(PlanningContextReader reader, PlanningContextMapper mapper, JsonMapper json) {
        this.reader = reader;
        this.mapper = mapper;
        this.json = json;
    }

    @Transactional
    public SessionPreferencesResponse update(UUID sessionId, int expectedRevision, PreferencePatchRequest request) {
        SessionRow session = reader.requireSession(sessionId);
        if (session.getRevision() != expectedRevision) {
            throw revisionMismatch(session.getRevision());
        }
        Weights weights = request.resolvedWeights();
        boolean unchanged = session.getPriorityPreset() == request.priorityPreset()
                && reader.weightsOf(session).equals(weights);
        if (unchanged) {
            return response(sessionId, session.getRevision(), request, weights);
        }
        int updated = mapper.updatePreferences(sessionId, expectedRevision, request.priorityPreset(),
                json.writeValueAsString(weights));
        if (updated == 0) {
            // 읽은 뒤 다른 요청이 먼저 저장한 경우
            throw revisionMismatch(reader.requireSession(sessionId).getRevision());
        }
        return response(sessionId, expectedRevision + 1, request, weights);
    }

    private static ApiException revisionMismatch(int current) {
        return new ApiException(ErrorCode.REVISION_MISMATCH,
                "다른 곳에서 입력이 바뀌었어요. 최신 내용을 불러온 뒤 다시 시도해 주세요. (현재 버전 " + current + ")");
    }

    private static SessionPreferencesResponse response(UUID sessionId, int revision, PreferencePatchRequest request,
                                                       Weights weights) {
        return new SessionPreferencesResponse(sessionId, revision, new Preferences(request.priorityPreset(), weights));
    }
}
