package com.aiyouth.pjt.model.enums;

/** 지원 합계 상태. 확인 불가 합계는 UNKNOWN + null, 확인된 0원은 CALCULATED + 0. */
public enum TotalStatus {
    CALCULATED, PARTIAL, UNKNOWN
}
