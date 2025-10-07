package com.foursales.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Product response DTO with Builder pattern
 * Benefits:
 * - Immutable: Thread-safe and prevents accidental modifications
 * - Clear construction: builder().field(value).build()
 * - Null-safe: Can omit optional fields
 */
@Getter
@Builder
@Schema(description = "Response containing product data")
public class ProductResponse {

    @Schema(description = "Unique product ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID id;

    @Schema(description = "Product name", example = "Notebook Dell Inspiron 15")
    private String name;

    @Schema(description = "Detailed product description", example = "Notebook with Intel i7 processor, 16GB RAM, 512GB SSD")
    private String description;

    @Schema(description = "Product unit price", example = "3500.00")
    private BigDecimal price;

    @Schema(description = "Product category", example = "Electronics")
    private String category;

    @Schema(description = "Available quantity in stock", example = "50")
    private Integer stockQuantity;

    @Schema(description = "Product creation date and time", example = "2025-01-15T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "Last update date and time", example = "2025-01-15T14:30:00")
    private LocalDateTime updatedAt;
}
