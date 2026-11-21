package com.aiyouth.pjt.global.exception;

import java.util.List;

public final class ApiException extends RuntimeException {
    private final int status;
    private final ErrorDetails error;

    public ApiException(int status, String code, String message, boolean retryable) {
        this(status, code, message, retryable, List.of());
    }

    private ApiException(int status, String code, String message, boolean retryable, List<FieldError> fields) {
        super(message);
        this.status = status;
        this.error = new ErrorDetails(code, message, fields, retryable);
    }

    public static ApiException invalid(String field, String code) {
        return new ApiException(422, "FIELD_INVALID", "입력값을 확인해 주세요.", false,
                List.of(new FieldError(field, code)));
    }

    public static ApiException withFields(int status, String code, String message, List<FieldError> fields) {
        return new ApiException(status, code, message, false, List.copyOf(fields));
    }

    public static ApiException missingDestination() {
        return new ApiException(422, "PROFILE_INCOMPLETE", "자주 가는 곳을 먼저 저장해 주세요.", false,
                List.of(new FieldError("profile.frequentPlace", "REQUIRED")));
    }

    public int status() {
        return status;
    }

    public ErrorBody body(String requestId) {
        return new ErrorBody(error, requestId);
    }

    public record FieldError(String field, String code) {}
    public record ErrorDetails(String code, String message, List<FieldError> fields, boolean retryable) {}
    public record ErrorBody(ErrorDetails error, String requestId) {}
}
