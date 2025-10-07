package com.foursales.ecommerce.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.foursales.ecommerce.exception.InsufficientStockException;
import com.foursales.ecommerce.exception.InvalidQuantityException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
@Document(indexName = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Product entity representing a product in the system")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @org.springframework.data.annotation.Id
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "Unique product identifier", example = "123e4567-e89b-12d3-a456-426614174000", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 100)
    @Column(name = "name", nullable = false)
    @Field(type = FieldType.Text, analyzer = "standard")
    @Schema(description = "Product name", example = "Dell Inspiron 15 Notebook", minLength = 2, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "Product description is required")
    @Size(max = 500)
    @Column(name = "description", nullable = false, length = 500)
    @Field(type = FieldType.Text)
    @Schema(description = "Detailed product description", example = "Notebook with Intel i7 processor, 16GB RAM, 512GB SSD", maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @NotNull(message = "Product price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    @Field(type = FieldType.Double)
    @Schema(description = "Unit price of the product", example = "3500.00", minimum = "0.01", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal price;

    @NotBlank(message = "Product category is required")
    @Size(min = 2, max = 50)
    @Column(name = "category", nullable = false)
    @Field(type = FieldType.Keyword)
    @Schema(description = "Product category", example = "Electronics", minLength = 2, maxLength = 50, requiredMode = Schema.RequiredMode.REQUIRED)
    private String category;

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    @Column(name = "stock_quantity", nullable = false)
    @Field(type = FieldType.Integer)
    @Schema(description = "Available stock quantity", example = "50", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer stockQuantity;

    @Column(name = "created_at", nullable = false)
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "Product creation timestamp", example = "2025-01-15T10:00:00", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Schema(description = "Product last update timestamp", example = "2025-01-15T14:30:00", accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime updatedAt;

    public Product(String name, String description, BigDecimal price, String category, Integer stockQuantity) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.stockQuantity = stockQuantity;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasStock(Integer requestedQuantity) {
        return this.stockQuantity >= requestedQuantity;
    }

    public void reduceStock(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new InvalidQuantityException("Quantity must be positive");
        }
        if (this.stockQuantity < quantity) {
            throw new InsufficientStockException(this.id, quantity, this.stockQuantity);
        }
        this.stockQuantity -= quantity;
    }
}
