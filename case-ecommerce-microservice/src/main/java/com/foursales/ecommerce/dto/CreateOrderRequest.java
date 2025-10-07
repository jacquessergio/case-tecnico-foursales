package com.foursales.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new order with multiple products")
public class CreateOrderRequest {

    @NotEmpty(message = "Item list cannot be empty")
    @Valid
    @Schema(description = "List of items to be included in the order. Each item must contain the product ID and desired quantity. System will validate if there is sufficient stock for all items.", required = true, minLength = 1, example = "[{\"productId\": \"a1b2c3d4-e5f6-7890-abcd-ef1234567890\", \"quantity\": 2}, {\"productId\": \"b2c3d4e5-f6a7-8901-bcde-f12345678901\", \"quantity\": 1}]")
    private List<OrderItemRequest> items;

    /**
     * IDEMPOTENCY KEY (Optional): Prevents duplicate order creation
     * Client should generate a unique UUID for each order creation request.
     * If key is provided and order already exists, returns existing order instead
     * of creating duplicate.
     * Useful for handling double-clicks, network retries, and browser back button.
     */
    @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
    @Schema(description = "Idempotency key (UUID) to prevent order duplication. Client should generate a unique UUID for each creation attempt.", example = "550e8400-e29b-41d4-a716-446655440000", maxLength = 100)
    private String idempotencyKey;

    public boolean hasIdempotencyKey() {
        return idempotencyKey != null && !idempotencyKey.isBlank();
    }
}