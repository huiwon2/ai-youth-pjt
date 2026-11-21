package com.aiyouth.pjt.global.exception;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return build(e.code(), e.getMessage(), e.fields());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorResponse.FieldError(f.getField(), "INVALID"))
                .toList();
        return build(ErrorCode.FIELD_INVALID, "입력값을 확인해 주세요.", fields);
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleParam(Exception e) {
        String name = e instanceof MissingServletRequestParameterException m ? m.getParameterName()
                : ((MethodArgumentTypeMismatchException) e).getName();
        return build(ErrorCode.FIELD_INVALID, "요청 값을 확인해 주세요.",
                List.of(new ErrorResponse.FieldError(name, "INVALID")));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleHeader(MissingRequestHeaderException e) {
        if ("If-Match".equalsIgnoreCase(e.getHeaderName())) {
            return build(ErrorCode.PRECONDITION_REQUIRED, "최신 입력 버전을 If-Match 헤더로 보내 주세요.", List.of());
        }
        return build(ErrorCode.FIELD_INVALID, "필수 헤더가 없어요.",
                List.of(new ErrorResponse.FieldError(e.getHeaderName(), "REQUIRED")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return build(ErrorCode.INVALID_JSON, "요청 형식을 확인해 주세요.", List.of());
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message, List<ErrorResponse.FieldError> fields) {
        String requestId = "req-" + UUID.randomUUID();
        if (code.status().is5xxServerError()) {
            log.warn("{} {} {}", requestId, code, message);
        }
        return ResponseEntity.status(code.status())
                .cacheControl(CacheControl.noStore())
                .body(new ErrorResponse(new ErrorResponse.Body(code.name(), message, fields, code.retryable()), requestId));
    }
}
