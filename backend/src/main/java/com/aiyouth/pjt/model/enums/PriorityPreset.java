package com.aiyouth.pjt.model.enums;

/**
 * 비용·시간·생활 순서의 우선순위 프리셋 (API 11).
 * 가중치는 명세 고정값이다. 점수 환산식은 보류 상태라 여기서 유도하지 않는다.
 */
public enum PriorityPreset {
    BALANCED(40, 35, 25),
    COST(60, 25, 15),
    COMMUTE(20, 60, 20),
    NEIGHBORHOOD(30, 20, 50),
    /** 사용자가 직접 조정한 가중치. 값은 planning_sessions.score_weights에 있다. */
    CUSTOM(-1, -1, -1);

    private final int cost;
    private final int commute;
    private final int convenience;

    PriorityPreset(int cost, int commute, int convenience) {
        this.cost = cost;
        this.commute = commute;
        this.convenience = convenience;
    }

    public int cost() {
        return cost;
    }

    public int commute() {
        return commute;
    }

    public int convenience() {
        return convenience;
    }
}
