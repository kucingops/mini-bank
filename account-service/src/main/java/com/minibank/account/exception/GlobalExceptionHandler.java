package com.minibank.account.exception;

import com.minibank.account.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;

/**
 * Maps exceptions to {@link ErrorResponse}. Only messages written for clients
 * ({@link ApiException}, validation, bad parameters) are returned as-is; database
 * and unexpected errors get a generic message and are logged with their details.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.warn("{} {}: {}", ex.getStatus().value(), request.getRequestURI(), ex.getMessage());
        } else {
            log.debug("{} {}: {}", ex.getStatus().value(), request.getRequestURI(), ex.getMessage());
        }
        return build(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<ErrorResponse> handleBadParameter(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON", request);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleBadSort(PropertyReferenceException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid sort property: " + ex.getPropertyName(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No endpoint " + ex.getHttpMethod() + " " + request.getRequestURI(), request);
    }

    /**
     * Database failures: never return the driver message, which contains SQL and schema details.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDataAccess(DataAccessException ex, HttpServletRequest request) {
        log.error("Database error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "A database error occurred", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), LocalDateTime.now()));
    }
}
