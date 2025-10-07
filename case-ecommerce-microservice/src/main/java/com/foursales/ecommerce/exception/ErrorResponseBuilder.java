package com.foursales.ecommerce.exception;

import com.foursales.ecommerce.dto.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

@Slf4j
@Component
public class ErrorResponseBuilder {

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    /**
     * Builds error response with automatic logging and sanitization
     *
     * @param ex          Exception that occurred
     * @param status      HTTP status code
     * @param userMessage User-friendly message (safe to display)
     * @param path        Request path where error occurred
     * @return ApiErrorResponse with sanitized details
     */
    public ApiErrorResponse buildErrorResponse(Exception ex, HttpStatus status, String userMessage, String path) {
        log.error("Exception occurred at path {}: ", path, ex);

        return ApiErrorResponse.builder()
                .code(status.value())
                .message(userMessage)
                .stackTrace(sanitizeStackTrace(ex))
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Builds error response for validation errors (safe to expose field names)
     *
     * @param validationMessage Validation error details
     * @param status            HTTP status code
     * @param userMessage       User-friendly message
     * @param path              Request path
     * @return ApiErrorResponse with validation details
     */
    public ApiErrorResponse buildValidationErrorResponse(String validationMessage, HttpStatus status,
            String userMessage, String path) {
        log.warn("Validation error at path {}: {}", path, validationMessage);

        return ApiErrorResponse.builder()
                .code(status.value())
                .message(userMessage)
                .stackTrace(validationMessage) // Validation errors are safe to return
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Sanitizes stack trace based on environment
     * Development: Full stack trace for debugging
     * Production: Only exception message (no internal details)
     */
    private String sanitizeStackTrace(Exception ex) {
        if (isDevelopmentMode()) {
            return getStackTraceAsString(ex);
        } else {
            // Production: Return only exception message (no stack trace)
            return ex.getMessage() != null ? ex.getMessage() : "Ocorreu um erro inesperado";
        }
    }

    /**
     * Determines if detailed error information should be exposed
     */
    private boolean isDevelopmentMode() {
        return "dev".equalsIgnoreCase(activeProfile) || "test".equalsIgnoreCase(activeProfile);
    }

    /**
     * Converts exception to full stack trace string
     */
    private String getStackTraceAsString(Exception ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }
}
