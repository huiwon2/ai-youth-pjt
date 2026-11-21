package com.aiyouth.pjt.global.exception;

import java.util.List;

/** {"error":{code,message,fields,retryable},"requestId"} */
public record ErrorResponse(Body error, String requestId) {

    public record Body(String code, String message, List<FieldError> fields, boolean retryable) {
    }

    public record FieldError(String field, String code) {
    }
}
