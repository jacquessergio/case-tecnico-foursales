package com.foursales.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response wrapper containing data in 'data' field")
public class ApiGenericResponse<T> {

    @Schema(description = "Response data", required = true)
    private T data;

    @Schema(description = "Optional message", example = "Operation completed successfully")
    private String message;

    @Schema(description = "Response timestamp", example = "2025-01-15T10:30:00")
    private String timestamp;

    public ApiGenericResponse(T data) {
        this.data = data;
        this.timestamp = java.time.LocalDateTime.now().toString();
    }

    public ApiGenericResponse(T data, String message) {
        this.data = data;
        this.message = message;
        this.timestamp = java.time.LocalDateTime.now().toString();
    }

    public static <T> ApiGenericResponse<T> success(T data) {
        return new ApiGenericResponse<>(data);
    }

    public static <T> ApiGenericResponse<T> success(T data, String message) {
        return new ApiGenericResponse<>(data, message);
    }
}
