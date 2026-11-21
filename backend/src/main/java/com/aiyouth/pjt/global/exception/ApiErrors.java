package com.aiyouth.pjt.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException.Reference;
import tools.jackson.databind.exc.PropertyBindingException;

@RestControllerAdvice
public class ApiErrors {
    public static final String REQUEST_ID = ApiErrors.class.getName() + ".requestId";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiException.ErrorBody> apiException(ApiException error, HttpServletRequest request) {
        return ResponseEntity.status(error.status()).body(error.body((String) request.getAttribute(REQUEST_ID)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiException.ErrorBody> invalidJson(HttpMessageNotReadableException error,
                                                                 HttpServletRequest request) {
        if (error.getMostSpecificCause() instanceof PropertyBindingException unknown) {
            String path = unknown.getPath().stream()
                    .map(Reference::getPropertyName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            return apiException(ApiException.invalid(path.isEmpty() ? unknown.getPropertyName() : path,
                    "UNKNOWN_FIELD"), request);
        }
        return apiException(new ApiException(400, "INVALID_JSON", "요청 JSON을 확인해 주세요.", false), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiException.ErrorBody> invalidParameter(MethodArgumentTypeMismatchException error,
                                                                     HttpServletRequest request) {
        return apiException(ApiException.invalid(error.getName(), "FORMAT"), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiException.ErrorBody> invalidBody(MethodArgumentNotValidException error,
                                                               HttpServletRequest request) {
        var fields = error.getBindingResult().getFieldErrors().stream()
                .map(field -> new ApiException.FieldError(field.getField(), "INVALID")).toList();
        return apiException(ApiException.withFields(422, "FIELD_INVALID", "입력값을 확인해 주세요.", fields), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiException.ErrorBody> missingParameter(MissingServletRequestParameterException error,
                                                                    HttpServletRequest request) {
        return apiException(ApiException.invalid(error.getParameterName(), "REQUIRED"), request);
    }
}
