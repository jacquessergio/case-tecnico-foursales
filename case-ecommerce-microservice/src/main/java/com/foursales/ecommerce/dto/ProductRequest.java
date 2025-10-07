package com.foursales.ecommerce.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create or update a product")
public class ProductRequest {

    @NotBlank
    @Size(min = 2, max = 100)
    @Schema(description = "Product name", example = "Notebook Dell Inspiron 15", minLength = 2, maxLength = 100, required = true)
    private String name;

    @NotBlank
    @Size(max = 500)
    @Schema(description = "Detailed product description", example = "Notebook with Intel i7 processor, 16GB RAM, 512GB SSD", maxLength = 500, required = true)
    private String description;

    @NotNull
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Schema(description = "Product unit price", example = "3500.00", minimum = "0.01", required = true)
    private BigDecimal price;

    @NotBlank
    @Size(min = 2, max = 50)
    @Schema(description = "Product category", example = "Electronics", minLength = 2, maxLength = 50, required = true)
    private String category;

    @NotNull
    @Min(value = 0, message = "Quantity cannot be negative")
    @Schema(description = "Available quantity in stock", example = "50", minimum = "0", required = true)
    private Integer stockQuantity;
}
