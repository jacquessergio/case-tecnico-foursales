package com.foursales.ecommerce.repository.jpa;

import com.foursales.ecommerce.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for OutboxEvent entity
 * Provides queries for the Transactional Outbox Pattern implementation
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Finds unpublished events ordered by creation time (FIFO)
     * Limits result to avoid overwhelming the publisher
     *
     * @return List of up to 100 unpublished events
     */
    List<OutboxEvent> findTop100ByPublishedFalseOrderByCreatedAtAsc();

    /**
     * Finds unpublished events for a specific aggregate
     * Useful for checking pending events for a specific order/product
     *
     * @param aggregateType Type of aggregate
     * @param aggregateId   ID of aggregate
     * @return List of unpublished events for the aggregate
     */
    List<OutboxEvent> findByAggregateTypeAndAggregateIdAndPublishedFalse(
            String aggregateType,
            String aggregateId);

    /**
     * Counts unpublished events
     * Useful for monitoring and alerting
     *
     * @return Number of events waiting to be published
     */
    long countByPublishedFalse();

    /**
     * Counts failed events (retry count > threshold)
     * Useful for detecting problematic events that need manual intervention
     *
     * @param retryThreshold Minimum retry count to consider as failed
     * @return Number of events that failed multiple times
     */
    @Query("SELECT COUNT(e) FROM OutboxEvent e WHERE e.published = false AND e.retryCount >= :retryThreshold")
    long countFailedEvents(@Param("retryThreshold") int retryThreshold);

    /**
     * Finds events stuck in retry loop (many retries but not published)
     * These events may need manual intervention
     *
     * @param minRetryCount Minimum number of retries
     * @return List of stuck events
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.published = false AND e.retryCount >= :minRetryCount ORDER BY e.createdAt ASC")
    List<OutboxEvent> findStuckEvents(@Param("minRetryCount") int minRetryCount);

    /**
     * Finds events by event type
     * Useful for debugging specific event types
     *
     * @param eventType Type of event
     * @param published Published status
     * @return List of events
     */
    List<OutboxEvent> findByEventTypeAndPublished(String eventType, Boolean published);

    /**
     * Deletes old published events (cleanup)
     *
     * @param beforeDate Delete events published before this date
     * @return Number of deleted events
     */
    long deleteByPublishedTrueAndPublishedAtBefore(LocalDateTime beforeDate);
}
