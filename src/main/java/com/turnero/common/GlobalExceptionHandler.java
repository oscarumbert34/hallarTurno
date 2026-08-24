package com.turnero.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MeterRegistry meterRegistry;

    public GlobalExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException exception, HttpServletRequest request) {
        if (exception.getStatus().is5xxServerError()) {
            log.error(
                    "api exception status={} path={} exception={}",
                    exception.getStatus().value(),
                    request.getRequestURI(),
                    exception.getClass().getName()
            );
            incrementErrorCounter(exception.getStatus());
        } else if (exception.getStatus().is4xxClientError()) {
            log.warn("api exception status={} path={}", exception.getStatus().value(), request.getRequestURI());
        }
        return buildResponse(exception.getStatus(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s: %s".formatted(error.getField(), error.getDefaultMessage()))
                .toList();

        log.warn("validation failed path={} violations={}", request.getRequestURI(), details.size());
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<String> details = exception.getConstraintViolations().stream()
                .map(violation -> "%s: %s".formatted(violation.getPropertyPath(), violation.getMessage()))
                .toList();

        log.warn("constraint validation failed path={} violations={}", request.getRequestURI(), details.size());
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", request, details);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleException(Exception exception, HttpServletRequest request) {
        log.error("unexpected error path={} exception={}", request.getRequestURI(), exception.getClass().getName());
        incrementErrorCounter(HttpStatus.INTERNAL_SERVER_ERROR);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error",
                request,
                List.of()
        );
    }

    private ResponseEntity<ApiError> buildResponse(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            List<String> details
    ) {
        ApiError apiError = ApiError.of(
                RequestIdFilter.currentRequestId(request),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                details
        );
        return ResponseEntity.status(status).body(apiError);
    }

    private void incrementErrorCounter(HttpStatus status) {
        meterRegistry.counter("turnero.http.errors", "status", String.valueOf(status.value())).increment();
    }
}
