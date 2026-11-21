package com.aiyouth.pjt.service.comparison;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiyouth.pjt.dto.request.comparison.PreferencePatchRequest;
import com.aiyouth.pjt.dto.response.PlanningResponse.Preferences;
import com.aiyouth.pjt.dto.response.PlanningResponse.Session;
import com.aiyouth.pjt.dto.response.PlanningResponse.Weights;
import com.aiyouth.pjt.global.revision.Revisions;
import com.aiyouth.pjt.mapper.PreferenceMapper;
import com.aiyouth.pjt.model.PlanningRows.SessionRow;
import com.aiyouth.pjt.service.PlanningSessions;
import com.aiyouth.pjt.service.SessionState;

import tools.jackson.databind.json.JsonMapper;

/**
 * API 11 우선순위 저장. 한 트랜잭션에서
 * 세션 행 잠금(소유권·만료) → If-Match 확인 → 저장과 revision +1(한 번) → 갱신된 행으로 전체 Session 응답을 만든다.
 * 값이 그대로면 revision을 올리지 않는다.
 */
@Service
public class PreferenceService {

    private final SessionState state;
    private final PlanningSessions sessions;
    private final PreferenceMapper mapper;
    private final JsonMapper json;

    public PreferenceService(SessionState state, PlanningSessions sessions, PreferenceMapper mapper, JsonMapper json) {
        this.state = state;
        this.sessions = sessions;
        this.mapper = mapper;
        this.json = json;
    }

    @Transactional
    public Session update(UUID sessionId, String token, String ifMatch, PreferencePatchRequest request) {
        SessionRow row = state.lockOwned(sessionId, token);
        Revisions.requireMatch(ifMatch, row.revision());
        PreferencePatchRequest.Weights target = request.resolvedWeights();
        if (unchanged(state.preferences(row), request.priorityPreset().name(), target)) {
            return sessions.response(row);
        }
        SessionRow updated = mapper.updatePreferences(sessionId, request.priorityPreset().name(),
                json.writeValueAsString(target), state.now());
        return sessions.response(updated);
    }

    private static boolean unchanged(Preferences current, String preset, PreferencePatchRequest.Weights target) {
        Weights w = current.weights();
        return current.priorityPreset().equals(preset) && same(w.cost(), target.cost())
                && same(w.commute(), target.commute()) && same(w.convenience(), target.convenience());
    }

    private static boolean same(BigDecimal current, int target) {
        return current != null && current.compareTo(BigDecimal.valueOf(target)) == 0;
    }
}
