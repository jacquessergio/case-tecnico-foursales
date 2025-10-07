package com.foursales.eventconsumer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity to store failed events for later reprocessing
 * This ensures eventual consistency even when external services are down
 */
@Entity
@Table(name = "failed_events", indexes = {
    @Index(name = "idx_failed_events_status", columnList = "status"),
    @Index(name = "idx_failed_events_topic_status", columnList = "original_topic, status"),
    @Index(name = "idx_failed_events_next_retry", columnList = "next_retry_at")
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailedEvent {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @JdbcTypeCode(SqlTypes.VARCHAR)  // Force string representation instead of binary
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "original_topic", nullable = false, length = 100)
    private String originalTopic;

    @Column(name = "event_key", length = 255)
    private String eventKey;

    @Column(name = "event_payload", nullable = false, columnDefinition = "TEXT")
    private String eventPayload;

    @Column(name = "exception_message", columnDefinition = "TEXT")
    private String exceptionMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FailedEventStatus status = FailedEventStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private Integer maxRetries = 10;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processing_notes", columnDefinition = "TEXT")
    private String processingNotes;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = FailedEventStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        // Calculate next retry time with exponential backoff
        calculateNextRetryTime();
    }

    /**
     * Calculates next retry time using exponential backoff
     * Formula: min(baseDelay * 2^retryCount, maxDelay)
     * Example: 1m, 2m, 4m, 8m, 16m, 32m, 64m (max)
     */
    public void calculateNextRetryTime() {
        if (status == FailedEventStatus.PENDING || status == FailedEventStatus.RETRYING) {
            long baseDelayMinutes = 1; // Start with 1 minute
            long maxDelayMinutes = 60; // Max 1 hour

            long delayMinutes = Math.min(
                baseDelayMinutes * (long) Math.pow(2, retryCount),
                maxDelayMinutes
            );

            nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
        }
    }

    /**
     * Increments retry count and updates retry timestamps
     */
    public void incrementRetryCount() {
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();
        calculateNextRetryTime();

        if (this.retryCount >= this.maxRetries) {
            this.status = FailedEventStatus.MAX_RETRIES_REACHED;
        }
    }

    /**
     * Marks the event as successfully processed
     */
    public void markAsProcessed(String notes) {
        this.status = FailedEventStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
        this.processingNotes = notes;
        this.nextRetryAt = null;
    }

    /**
     * Marks the event as permanently failed
     */
    public void markAsFailed(String notes) {
        this.status = FailedEventStatus.FAILED;
        this.processingNotes = notes;
        this.nextRetryAt = null;
    }

    public enum FailedEventStatus {
        PENDING,           // Waiting for first retry
        RETRYING,          // Currently being retried
        PROCESSED,         // Successfully processed
        FAILED,            // Permanently failed (manual intervention needed)
        MAX_RETRIES_REACHED // Max retries reached, needs manual review
    }
}