package com.aiyouth.pjt.service.planning;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiyouth.pjt.dto.response.common.CommonTypes.Weights;
import com.aiyouth.pjt.global.exception.ApiException;
import com.aiyouth.pjt.global.exception.ErrorCode;
import com.aiyouth.pjt.mapper.PlanningContextMapper;
import com.aiyouth.pjt.model.enums.PriorityPreset;
import com.aiyouth.pjt.model.planning.ProfileRow;
import com.aiyouth.pjt.model.planning.SessionRow;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 세션 소유 확인과 계산 입력 읽기.
 * <p>
 * TODO(백엔드 1 연동): 작업 쿠키의 토큰 해시를 planning_sessions.access_token_hash와 비교하는 소유권 검사는
 * 백엔드 1의 세션 인증 코드로 대체한다. 지금은 세션 존재(404)와 만료(401)만 검사한다.
 */
@Service
public class PlanningContextReader {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PlanningContextMapper mapper;
    private final JsonMapper json;
    private final Clock clock;

    @Autowired
    public PlanningContextReader(PlanningContextMapper mapper, JsonMapper json) {
        this(mapper, json, Clock.system(KST));
    }

    PlanningContextReader(PlanningContextMapper mapper, JsonMapper json, Clock clock) {
        this.mapper = mapper;
        this.json = json;
        this.clock = clock;
    }

    /** 세션·프로필·목적지·후보를 한 트랜잭션에서 읽는다. */
    @Transactional(readOnly = true)
    public PlanningContext load(UUID sessionId) {
        SessionRow session = requireSession(sessionId);
        ProfileRow profile = mapper.findProfile(sessionId);
        JsonNode answers = profile == null || profile.getAdditionalAnswers() == null
                ? json.createObjectNode() : json.readTree(profile.getAdditionalAnswers());
        return new PlanningContext(session, profile, answers, mapper.findDestination(sessionId),
                mapper.findHomes(sessionId), weightsOf(session), today());
    }

    public SessionRow requireSession(UUID sessionId) {
        SessionRow session = mapper.findSession(sessionId);
        if (session == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "작업을 찾을 수 없어요. 새로 시작해 주세요.");
        }
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(OffsetDateTime.now(clock))) {
            throw new ApiException(ErrorCode.SESSION_EXPIRED, "작업이 만료됐어요. 새로 시작해 주세요.");
        }
        return session;
    }

    public Weights weightsOf(SessionRow session) {
        if (session.getPriorityPreset() != PriorityPreset.CUSTOM) {
            return Weights.of(session.getPriorityPreset());
        }
        return json.readValue(session.getScoreWeights(), Weights.class);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
