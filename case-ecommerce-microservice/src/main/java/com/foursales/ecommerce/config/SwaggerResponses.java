package com.foursales.ecommerce.config;

import com.foursales.ecommerce.dto.ApiErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.lang.annotation.*;

/**
 * Centralized Swagger response annotations to eliminate repetition across controllers
 *
 * Usage:
 * <pre>
 * @SwaggerResponses.NotFound
 * @SwaggerResponses.InternalError
 * public ResponseEntity<ProductResponse> getProductById(@PathVariable UUID id) { ... }
 * </pre>
 */
public class SwaggerResponses {

    /**
     * 400 Bad Request - Validation errors
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(
        responseCode = "400",
        description = "Invalid validation parameters",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(
                value = "{\"code\": 400, \"message\": \"Validation error\", \"stackTrace\": \"Validation error details\"}"
            )
        )
    )
    public @interface BadRequest {}

    /**
     * 403 Forbidden - Access denied
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(
        responseCode = "403",
        description = "Access denied - admin only",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(
                value = "{\"code\": 403, \"message\": \"Access denied\", \"stackTrace\": \"You do not have permission to access this resource\"}"
            )
        )
    )
    public @interface Forbidden {}

    /**
     * 401 Unauthorized - Invalid credentials
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(
        responseCode = "401",
        description = "Invalid credentials",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(
                value = "{\"code\": 401, \"message\": \"Invalid credentials\", \"stackTrace\": \"Incorrect email or password\"}"
            )
        )
    )
    public @interface Unauthorized {}

    /**
     * 404 Not Found - Resource not found
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(
        responseCode = "404",
        description = "Resource not found",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(
                value = "{\"code\": 404, \"message\": \"Resource not found\", \"stackTrace\": \"Resource with specified ID not found\"}"
            )
        )
    )
    public @interface NotFound {}

    /**
     * 500 Internal Server Error
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(
        responseCode = "500",
        description = "Internal server error",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = ApiErrorResponse.class),
            examples = @ExampleObject(
                value = "{\"code\": 500, \"message\": \"Internal server error\", \"stackTrace\": \"Unexpected error while processing the request\"}"
            )
        )
    )
    public @interface InternalError {}
}
