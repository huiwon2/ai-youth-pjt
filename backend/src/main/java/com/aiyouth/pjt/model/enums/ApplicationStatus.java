package com.aiyouth.pjt.model.enums;

/** 접수 상태. 날짜가 없다고 ALWAYS로 해석하지 않고 UNKNOWN으로 둔다. */
public enum ApplicationStatus {
    OPEN, ALWAYS, UPCOMING, CLOSED, UNKNOWN
}
