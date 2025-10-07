package com.foursales.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event payload for product synchronization via Kafka
 * Ensures eventual consistency between MySQL and Elasticsearch
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSyncEvent {

    private UUID productId;
    private String eventType; // CREATED, UPDATED, DELETED
    private LocalDateTime eventTimestamp;
    private String eventId; // For idempotency

    // Product data (null for DELETE events)
    private String name;
    private String description;
    private BigDecimal price;
    private String category;
    private Integer stockQuantity;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
