package com.foursales.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Order item response DTO with Builder pattern
 * Immutable to prevent accidental modifications
 */
@Getter
@Builder
@Schema(description = "Individual order item with product information")
public class OrderItemResponse {

    @Schema(description = "Unique order item ID", example = "c3d4e5f6-a7b8-9012-cdef-123456789012")
    private UUID id;

    @Schema(description = "Product ID", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private UUID productId;

    @Schema(description = "Product name", example = "Notebook Dell Inspiron 15")
    private String productName;

    @Schema(description = "Product quantity in the order", example = "2")
    private Integer quantity;

    @Schema(description = "Unit price of the product at the time of purchase", example = "3500.00")
    private BigDecimal unitPrice;

    @Schema(description = "Total item value (quantity x unit price)", example = "7000.00")
    private BigDecimal subtotal;
}
