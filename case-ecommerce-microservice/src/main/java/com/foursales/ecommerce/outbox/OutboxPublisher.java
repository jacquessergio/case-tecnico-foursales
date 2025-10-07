package com.foursales.ecommerce.outbox;

import com.foursales.ecommerce.entity.OutboxEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxService outboxService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void publishPendingEvents() {
        try {
            List<OutboxEvent> pendingEvents = outboxService.getUnpublishedEvents();

            if (pendingEvents.isEmpty()) {
                return;
            }

            log.debug("Publishing {} pending outbox events", pendingEvents.size());

            int successCount = 0;
            int failureCount = 0;
            int skippedCount = 0;

            for (OutboxEvent event : pendingEvents) {
                if (!event.shouldRetry()) {
                    log.error("Event {} has exceeded max retries ({}). Skipping. Manual intervention required.",
                            event.getId(), event.getRetryCount());
                    skippedCount++;
                    continue;
                }

                if (event.getRetryCount() > 0) {
                    long backoffSeconds = event.getBackoffDelaySeconds();
                    long timeSinceCreation = java.time.Duration.between(
                            event.getCreatedAt(),
                            java.time.LocalDateTime.now()).getSeconds();

                    if (timeSinceCreation < backoffSeconds) {
                        log.debug("Event {} is in backoff period. Waiting {} more seconds.",
                                event.getId(), backoffSeconds - timeSinceCreation);
                        continue;
                    }
                }

                boolean published = publishEvent(event);

                if (published) {
                    successCount++;
                } else {
                    failureCount++;
                }
            }

            if (successCount > 0 || failureCount > 0 || skippedCount > 0) {
                log.info("Outbox publishing completed. Success: {}, Failed: {}, Skipped: {}, Pending: {}",
                        successCount, failureCount, skippedCount,
                        outboxService.getUnpublishedCount());
            }

        } catch (Exception e) {
            log.error("Error in outbox publisher job", e);
        }
    }

    // CIRCUIT BREAKER: Protects against Kafka failures to prevent thread pool
    // exhaustion
    @CircuitBreaker(name = "kafka", fallbackMethod = "publishEventFallback")
    private boolean publishEvent(OutboxEvent event) {
        try {
            log.debug("Publishing event {} to topic {} (retry count: {})",
                    event.getId(), event.getTopic(), event.getRetryCount());

            SendResult<String, Object> result = sendToKafkaAndWait(event);
            markEventAsPublished(event, result);
            return true;

        } catch (Exception e) {
            handlePublishFailure(event, e);
            return false;
        }
    }

    private SendResult<String, Object> sendToKafkaAndWait(OutboxEvent event) throws Exception {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                event.getTopic(),
                event.getPartitionKey(),
                event.getPayload());
        return future.get();
    }

    private void markEventAsPublished(OutboxEvent event, SendResult<String, Object> result) {
        outboxService.markAsPublished(event);

        log.info("Successfully published event {} to topic {} (partition: {}, offset: {})",
                event.getId(),
                event.getTopic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
    }

    private void handlePublishFailure(OutboxEvent event, Exception e) {
        String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        outboxService.recordFailure(event, errorMessage);

        log.error("Failed to publish event {} to topic {} (retry {}/10): {}",
                event.getId(),
                event.getTopic(),
                event.getRetryCount(),
                errorMessage);
    }

    public boolean publishEventFallback(OutboxEvent event, Throwable throwable) {
        log.error("Circuit Breaker OPEN: Kafka is unreachable. Skipping event {} (will retry in 5s). Error: {}",
                event.getId(), throwable.getMessage());
        return false;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void cleanupOldEvents() {
        try {
            int retentionDays = 7;
            long deletedCount = outboxService.cleanupOldEvents(retentionDays);

            if (deletedCount > 0) {
                log.info("Cleaned up {} old outbox events (retention: {} days)",
                        deletedCount, retentionDays);
            }
        } catch (Exception e) {
            log.error("Error cleaning up old outbox events", e);
        }
    }

    @Scheduled(fixedDelay = 900000, initialDelay = 60000)
    public void monitorStuckEvents() {
        try {
            long failedCount = outboxService.getFailedCount(5);

            if (failedCount > 0) {
                log.warn("ALERT: {} outbox events have failed 5+ times. Manual intervention may be required.",
                        failedCount);

                List<OutboxEvent> stuckEvents = outboxService.getStuckEvents(5);
                for (OutboxEvent event : stuckEvents) {
                    log.warn("Stuck event: ID={}, Type={}, Aggregate={}, Retries={}, LastError={}",
                            event.getId(),
                            event.getEventType(),
                            event.getAggregateId(),
                            event.getRetryCount(),
                            event.getLastError());
                }
            }

            long totalPending = outboxService.getUnpublishedCount();
            if (totalPending > 1000) {
                log.warn("ALERT: {} unpublished events in outbox. Kafka may be slow or down.",
                        totalPending);
            }

        } catch (Exception e) {
            log.error("Error monitoring stuck events", e);
        }
    }
}
