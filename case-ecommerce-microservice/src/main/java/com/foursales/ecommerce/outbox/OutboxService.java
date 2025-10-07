package com.foursales.ecommerce.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foursales.ecommerce.entity.OutboxEvent;
import com.foursales.ecommerce.repository.jpa.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing Outbox events
 * Handles creation and querying of events in the Transactional Outbox Pattern
 *
 * IMPORTANT: All save methods use PROPAGATION.MANDATORY
 * This ensures they participate in the caller's transaction
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Saves an event to the outbox
     * MUST be called within an active transaction
     *
     * @param aggregateType Type of aggregate (e.g., "ORDER", "PRODUCT")
     * @param aggregateId   ID of the aggregate
     * @param eventType     Type of event (e.g., "ORDER_PAID", "PRODUCT_CREATED")
     * @param payload       Event payload object (will be serialized to JSON)
     * @param topic         Kafka topic to publish to
     * @return Saved OutboxEvent
     * @throws IllegalStateException if no transaction is active
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent saveEvent(
            String aggregateType,
            String aggregateId,
            String eventType,
            Object payload,
            String topic) {

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);

            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payloadJson)
                    .topic(topic)
                    .partitionKey(aggregateId)
                    .createdAt(LocalDateTime.now())
                    .published(false)
                    .retryCount(0)
                    .build();

            OutboxEvent savedEvent = outboxEventRepository.save(event);

            log.debug("Saved outbox event: {} for {} {}", eventType, aggregateType, aggregateId);

            return savedEvent;

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payload for event {} of {} {}",
                    eventType, aggregateType, aggregateId, e);
            throw new RuntimeException("Failed to serialize event payload", e);
        }
    }

    /**
     * Overloaded method for simple string payloads
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent saveEvent(
            String aggregateType,
            String aggregateId,
            String eventType,
            String payloadJson,
            String topic) {

        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payloadJson)
                .topic(topic)
                .partitionKey(aggregateId)
                .createdAt(LocalDateTime.now())
                .published(false)
                .retryCount(0)
                .build();

        return outboxEventRepository.save(event);
    }

    /**
     * Gets unpublished events ready for publishing
     * Limited to 100 events per batch to avoid overwhelming Kafka
     *
     * @return List of unpublished events
     */
    @Transactional(readOnly = true)
    public List<OutboxEvent> getUnpublishedEvents() {
        return outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();
    }

    /**
     * Marks an event as successfully published
     *
     * @param event Event to mark as published
     */
    @Transactional
    public void markAsPublished(OutboxEvent event) {
        event.markAsPublished();
        outboxEventRepository.save(event);
        log.debug("Marked event {} as published", event.getId());
    }

    /**
     * Records a failed publishing attempt
     *
     * @param event        Event that failed to publish
     * @param errorMessage Error message
     */
    @Transactional
    public void recordFailure(OutboxEvent event, String errorMessage) {
        event.recordFailure(errorMessage);
        outboxEventRepository.save(event);
        log.warn("Recorded failure for event {}: {} (retry count: {})",
                event.getId(), errorMessage, event.getRetryCount());
    }

    /**
     * Gets count of unpublished events
     * Useful for monitoring
     *
     * @return Number of events waiting to be published
     */
    @Transactional(readOnly = true)
    public long getUnpublishedCount() {
        return outboxEventRepository.countByPublishedFalse();
    }

    /**
     * Gets count of failed events (retried many times but still not published)
     *
     * @param retryThreshold Minimum retry count to consider as failed
     * @return Number of failed events
     */
    @Transactional(readOnly = true)
    public long getFailedCount(int retryThreshold) {
        return outboxEventRepository.countFailedEvents(retryThreshold);
    }

    /**
     * Gets stuck events (many retries but not published)
     * These events may need manual intervention
     *
     * @param minRetryCount Minimum number of retries
     * @return List of stuck events
     */
    @Transactional(readOnly = true)
    public List<OutboxEvent> getStuckEvents(int minRetryCount) {
        return outboxEventRepository.findStuckEvents(minRetryCount);
    }

    /**
     * Checks if there are pending events for a specific aggregate
     * Useful for ensuring all events are processed before deleting an aggregate
     *
     * @param aggregateType Type of aggregate
     * @param aggregateId   ID of aggregate
     * @return true if there are unpublished events
     */
    @Transactional(readOnly = true)
    public boolean hasPendingEvents(String aggregateType, String aggregateId) {
        List<OutboxEvent> pending = outboxEventRepository
                .findByAggregateTypeAndAggregateIdAndPublishedFalse(aggregateType, aggregateId);
        return !pending.isEmpty();
    }

    /**
     * Cleans up old published events
     * Should be called periodically to avoid table bloat
     *
     * @param retentionDays Number of days to retain published events
     * @return Number of deleted events
     */
    @Transactional
    public long cleanupOldEvents(int retentionDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        long deletedCount = outboxEventRepository
                .deleteByPublishedTrueAndPublishedAtBefore(cutoffDate);

        if (deletedCount > 0) {
            log.info("Cleaned up {} old outbox events (older than {} days)",
                    deletedCount, retentionDays);
        }

        return deletedCount;
    }

    /**
     * Gets events by type and status
     * Useful for debugging
     *
     * @param eventType Type of event
     * @param published Published status
     * @return List of matching events
     */
    @Transactional(readOnly = true)
    public List<OutboxEvent> getEventsByType(String eventType, Boolean published) {
        return outboxEventRepository.findByEventTypeAndPublished(eventType, published);
    }

    /**
     * Gets all events for an aggregate (published and unpublished)
     *
     * @param aggregateType Type of aggregate
     * @param aggregateId   ID of aggregate
     * @return List of all events for the aggregate
     */
    @Transactional(readOnly = true)
    public List<OutboxEvent> getEventsForAggregate(String aggregateType, String aggregateId) {
        return outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateType().equals(aggregateType)
                        && e.getAggregateId().equals(aggregateId))
                .toList();
    }

    /**
     * Manually retries a stuck event
     * Resets retry count and clears error message
     *
     * @param eventId ID of event to retry
     */
    @Transactional
    public void manualRetry(Long eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found: " + eventId));

        event.setRetryCount(0);
        event.setLastError(null);
        outboxEventRepository.save(event);

        log.info("Manually reset event {} for retry", eventId);
    }
}
