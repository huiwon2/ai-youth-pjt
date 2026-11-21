package com.aiyouth.pjt.service.planning;

import java.time.LocalDate;
import java.util.List;

import com.aiyouth.pjt.calculator.AgeCalculator;
import com.aiyouth.pjt.calculator.AgeCalculator.AgeRange;
import com.aiyouth.pjt.dto.response.common.CommonTypes.Weights;
import com.aiyouth.pjt.model.enums.PriorityPreset;
import com.aiyouth.pjt.model.planning.DestinationRow;
import com.aiyouth.pjt.model.planning.HomeRow;
import com.aiyouth.pjt.model.planning.ProfileRow;
import com.aiyouth.pjt.model.planning.SessionRow;

import tools.jackson.databind.JsonNode;

/**
 * 한 요청에서 읽은 세션 상태. 계산은 이 스냅샷만 사용한다.
 *
 * @param assessmentDate 판정 기준일 (요청 시점의 한국 날짜)
 * @param answers        profiles.additional_answers. 없으면 빈 객체 노드
 */
public record PlanningContext(SessionRow session, ProfileRow profile, JsonNode answers, DestinationRow destination,
                              List<HomeRow> homes, Weights weights, LocalDate assessmentDate) {

    public int revision() {
        return session.getRevision();
    }

    public PriorityPreset priorityPreset() {
        return session.getPriorityPreset();
    }

    public boolean hasProfile() {
        return profile != null;
    }

    public AgeRange ageRange() {
        Integer birthYear = intAnswer("birthYear");
        if (birthYear != null) {
            Boolean passed = boolAnswer("birthdayPassedThisYear");
            String asOf = textAnswer("birthdayAnswerAsOfDate");
            return AgeCalculator.ageRange(birthYear, passed, asOf == null ? null : LocalDate.parse(asOf),
                    assessmentDate);
        }
        // 출생연도 답변이 없고 서버가 같은 판정일에 확정한 나이만 있는 경우
        if (profile != null && profile.getAgeYears() != null && assessmentDate.equals(profile.getAgeAsOfDate())) {
            int age = profile.getAgeYears();
            return new AgeRange(age, age, age);
        }
        return null;
    }

    public Long monthlyIncomeWon() {
        return profile == null ? null : profile.getMonthlyIncomeWon();
    }

    public Long availableCashWon() {
        return profile == null ? null : profile.getAvailableCashWon();
    }

    public String destinationName() {
        return destination == null ? null : destination.getName();
    }

    private Integer intAnswer(String key) {
        JsonNode n = answers.get(key);
        return n != null && n.isIntegralNumber() ? n.intValue() : null;
    }

    private Boolean boolAnswer(String key) {
        JsonNode n = answers.get(key);
        return n != null && n.isBoolean() ? n.booleanValue() : null;
    }

    private String textAnswer(String key) {
        JsonNode n = answers.get(key);
        return n != null && n.isString() ? n.stringValue() : null;
    }
}
