package com.aiyouth.pjt.global.exception;

import org.springframework.http.HttpStatus;

/** API 명세 「공통 오류」의 error.code와 HTTP 상태. */
public enum ErrorCode {
    INVALID_JSON(HttpStatus.BAD_REQUEST, false),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, false),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, false),
    REVISION_MISMATCH(HttpStatus.PRECONDITION_FAILED, false),
    PRECONDITION_REQUIRED(HttpStatus.PRECONDITION_REQUIRED, false),
    STALE_INPUT(HttpStatus.CONFLICT, false),
    STALE_COMPARISON(HttpStatus.CONFLICT, false),
    PREVIEW_EXPIRED(HttpStatus.CONFLICT, false),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, false),
    FIELD_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, false),
    PROFILE_INCOMPLETE(HttpStatus.UNPROCESSABLE_CONTENT, false),
    ADDRESS_NOT_SELECTABLE(HttpStatus.UNPROCESSABLE_CONTENT, false),
    HOME_COUNT_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, false),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, true),
    UPSTREAM_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, true),
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, true);

    private final HttpStatus status;
    private final boolean retryable;

    ErrorCode(HttpStatus status, boolean retryable) {
        this.status = status;
        this.retryable = retryable;
    }

    public HttpStatus status() {
        return status;
    }

    public boolean retryable() {
        return retryable;
    }
}
