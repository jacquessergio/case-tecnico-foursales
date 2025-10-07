package com.foursales.eventconsumer.entity;

import com.foursales.eventconsumer.exception.InsufficientStockException;
import jakarta.persistence.*;
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
@AllArgsConstructor
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @org.springframework.data.annotation.Id
    private UUID id;

    @Column(nullable = false)
    @Field(type = FieldType.Text, analyzer = "standard")
    private String name;

    @Column(nullable = false, length = 500)
    @Field(type = FieldType.Text)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    @Field(type = FieldType.Double)
    private BigDecimal price;

    @Column(nullable = false)
    @Field(type = FieldType.Keyword)
    private String category;

    @Column(name = "stock_quantity", nullable = false)
    @Field(type = FieldType.Integer)
    private Integer stockQuantity;

    @Column(name = "created_at", nullable = false)
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime updatedAt;

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

    public void reduceStock(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (this.stockQuantity < quantity) {
            throw new InsufficientStockException(this.id, quantity, this.stockQuantity);
        }
        this.stockQuantity -= quantity;
        this.updatedAt = LocalDateTime.now();
    }

}