package com.foursales.ecommerce.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Outbox Event entity for implementing the Transactional Outbox Pattern
 * Ensures at-least-once delivery of events to Kafka with ACID guarantees
 *
 * Pattern flow:
 * 1. Business logic + OutboxEvent saved in SAME transaction (atomic)
 * 2. Background job polls unpublished events
 * 3. Publishes to Kafka with retry logic
 * 4. Marks event as published after Kafka confirmation
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_published", columnList = "published, created_at"),
    @Index(name = "idx_aggregate", columnList = "aggregate_type, aggregate_id"),
    @Index(name = "idx_event_type", columnList = "event_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Type of aggregate that generated this event
     * Examples: "ORDER", "PRODUCT", "USER"
     */
    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    /**
     * ID of the aggregate that generated this event
     * Usually a UUID converted to String
     */
    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    /**
     * Type of event that occurred
     * Examples: "ORDER_PAID", "PRODUCT_CREATED", "PRODUCT_UPDATED"
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /**
     * JSON payload of the event
     * Contains all necessary data for consumers to process the event
     */
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * Timestamp when the event was created (and business transaction committed)
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Whether the event has been successfully published to Kafka
     */
    @Column(name = "published", nullable = false)
    @Builder.Default
    private Boolean published = false;

    /**
     * Timestamp when the event was published to Kafka
     */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /**
     * Number of times we attempted to publish this event
     * Useful for identifying problematic events
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * Last error message if publishing failed
     * Helps with debugging and monitoring
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /**
     * Kafka topic where this event should be published
     * Stored to make publisher more flexible
     */
    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    /**
     * Kafka partition key (usually aggregate ID)
     * Ensures ordering for events of the same aggregate
     */
    @Column(name = "partition_key", nullable = false, length = 100)
    private String partitionKey;

    /**
     * Marks the event as published
     */
    public void markAsPublished() {
        this.published = true;
        this.publishedAt = LocalDateTime.now();
    }

    /**
     * Records a failed publishing attempt
     */
    public void recordFailure(String errorMessage) {
        this.retryCount++;
        this.lastError = errorMessage != null && errorMessage.length() > 1000
            ? errorMessage.substring(0, 1000)
            : errorMessage;
    }

    /**
     * Checks if the event should be retried based on retry count
     * Uses exponential backoff strategy
     */
    public boolean shouldRetry() {
        // Max 10 retries
        if (retryCount >= 10) {
            return false;
        }
        return true;
    }

    /**
     * Calculates backoff delay in seconds based on retry count
     * Exponential backoff: 1s, 2s, 4s, 8s, 16s, 32s, 64s, 128s, 256s, 512s
     */
    public long getBackoffDelaySeconds() {
        return (long) Math.pow(2, Math.min(retryCount, 9));
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (published == null) {
            published = false;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}
