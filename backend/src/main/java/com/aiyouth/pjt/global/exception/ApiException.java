package com.aiyouth.pjt.global.exception;

import java.util.List;

/** 명세의 ErrorResponse로 변환되는 예외. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<ErrorResponse.FieldError> fields;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ApiException(ErrorCode code, String message, List<ErrorResponse.FieldError> fields) {
        super(message);
        this.code = code;
        this.fields = fields;
    }

    public static ApiException field(ErrorCode code, String message, String field, String fieldCode) {
        return new ApiException(code, message, List.of(new ErrorResponse.FieldError(field, fieldCode)));
    }

    public ErrorCode code() {
        return code;
    }

    public List<ErrorResponse.FieldError> fields() {
        return fields;
    }
}
