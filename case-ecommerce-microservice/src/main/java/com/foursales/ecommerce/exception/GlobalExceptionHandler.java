package com.foursales.ecommerce.exception;

import com.foursales.ecommerce.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

        private final ErrorResponseBuilder errorResponseBuilder;

        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ApiErrorResponse> handleResourceNotFoundException(
                        ResourceNotFoundException ex,
                        HttpServletRequest request) {

                ApiErrorResponse error = errorResponseBuilder.buildErrorResponse(
                                ex,
                                HttpStatus.NOT_FOUND,
                                "Resource not found",
                                request.getRequestURI());

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        @ExceptionHandler(BusinessException.class)
        public ResponseEntity<ApiErrorResponse> handleBusinessException(
                        BusinessException ex,
                        HttpServletRequest request) {

                ApiErrorResponse error = errorResponseBuilder.buildErrorResponse(
                                ex,
                                HttpStatus.BAD_REQUEST,
                                "Business validation error",
                                request.getRequestURI());

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }

        @ExceptionHandler(AccountLockedException.class)
        public ResponseEntity<ApiErrorResponse> handleAccountLockedException(
                        AccountLockedException ex,
                        HttpServletRequest request) {

                ApiErrorResponse error = errorResponseBuilder.buildErrorResponse(
                                ex,
                                HttpStatus.LOCKED,
                                "Account locked",
                                request.getRequestURI());

                return ResponseEntity.status(HttpStatus.LOCKED).body(error);
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiErrorResponse> handleValidationException(
                        MethodArgumentNotValidException ex,
                        HttpServletRequest request) {

                String validationErrors = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(error -> "The field " + error.getField() + " " + error.getDefaultMessage())
                                .collect(Collectors.joining(", "));

                ApiErrorResponse error = errorResponseBuilder.buildValidationErrorResponse(
                                validationErrors,
                                HttpStatus.BAD_REQUEST,
                                "Validation error",
                                request.getRequestURI());

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }

        @ExceptionHandler(BadCredentialsException.class)
        public ResponseEntity<ApiErrorResponse> handleBadCredentialsException(
                        BadCredentialsException ex,
                        HttpServletRequest request) {

                // SECURITY: Never reveal which field is wrong (username or password)
                ApiErrorResponse error = errorResponseBuilder.buildErrorResponse(
                                ex,
                                HttpStatus.UNAUTHORIZED,
                                "Invalid credentials",
                                request.getRequestURI());

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ApiErrorResponse> handleAccessDeniedException(
                        AccessDeniedException ex,
                        HttpServletRequest request) {

                ApiErrorResponse error = errorResponseBuilder.buildErrorResponse(
                                ex,
                                HttpStatus.FORBIDDEN,
                                "Access denied",
                                request.getRequestURI());

                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadableException(
                        HttpMessageNotReadableException ex,
                        HttpServletRequest request) {

                ApiErrorResponse error = errorResponseBuilder.buildErrorResponse(
                                ex,
                                HttpStatus.BAD_REQUEST,
                                "Validation error",
                                request.getRequestURI());

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiErrorResponse> handleGenericException(
                        Exception ex,
                        HttpServletRequest request) {

                ApiErrorResponse error = errorResponseBuilder.buildErrorResponse(
                                ex,
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Internal server error",
                                request.getRequestURI());

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
}
