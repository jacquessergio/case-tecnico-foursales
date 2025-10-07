package com.foursales.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Individual order item containing product reference and desired quantity")
public class OrderItemRequest {

    @NotNull(message = "Product ID is required")
    @Schema(description = "Unique ID of the product to be added to the order. The product must exist in the catalog and have sufficient stock.", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890", required = true)
    private UUID productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be greater than zero")
    @Schema(description = "Desired quantity of product units. Must be greater than zero and cannot exceed the available quantity in stock.", example = "3", minimum = "1", required = true)
    private Integer quantity;
}