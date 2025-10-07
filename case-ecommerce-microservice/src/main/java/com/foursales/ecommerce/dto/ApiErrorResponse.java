package com.foursales.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API error response")
public class ApiErrorResponse {

    @Schema(description = "HTTP error code", example = "404")
    private Integer code;

    @Schema(description = "User-friendly error message", example = "Product not found")
    private String message;

    @Schema(description = "Technical error details or stack trace", example = "Product with id 123e4567-e89b-12d3-a456-426614174000 not found")
    private String stackTrace;

    @Schema(description = "Error timestamp", example = "2025-01-15T14:30:00")
    private LocalDateTime timestamp;

    @Schema(description = "Request path that generated the error", example = "/api/products/123e4567-e89b-12d3-a456-426614174000")
    private String path;

    public ApiErrorResponse(Integer code, String message, String stackTrace) {
        this.code = code;
        this.message = message;
        this.stackTrace = stackTrace;
        this.timestamp = LocalDateTime.now();
    }

    public ApiErrorResponse(Integer code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }
}
