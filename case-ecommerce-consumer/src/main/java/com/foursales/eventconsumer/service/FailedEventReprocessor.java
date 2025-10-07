package com.foursales.eventconsumer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foursales.eventconsumer.dto.ProductSyncEvent;
import com.foursales.eventconsumer.entity.FailedEvent;
import com.foursales.eventconsumer.repository.jpa.FailedEventRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FailedEventReprocessor {

    private final FailedEventRepository failedEventRepository;
    private final StockUpdateService stockUpdateService;
    private final ProductSyncService productSyncService;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 10;

    @Scheduled(fixedDelay = 120000, initialDelay = 60000)
    public void reprocessFailedEvents() {
        LocalDateTime now = LocalDateTime.now();

        List<FailedEvent> eventsToRetry = failedEventRepository.findEventsReadyForRetry(
                now,
                PageRequest.of(0, BATCH_SIZE));

        if (eventsToRetry.isEmpty()) {
            log.debug("No failed events ready for reprocessing");
            return;
        }

        log.info("Found {} failed events ready for reprocessing", eventsToRetry.size());

        for (FailedEvent event : eventsToRetry) {
            try {
                processEventInTransaction(event, now);
            } catch (Exception e) {
                log.error(" Failed to reprocess event {}: {}", event.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    protected void processEventInTransaction(FailedEvent event, LocalDateTime now) {
        event.setStatus(FailedEvent.FailedEventStatus.RETRYING);
        event.setLastRetryAt(now);
        failedEventRepository.save(event);

        try {
            boolean success = reprocessEvent(event);

            if (success) {
                event.markAsProcessed("Successfully reprocessed after " + event.getRetryCount() + " retries");
                log.info("Successfully reprocessed event {} from topic {} after {} retries",
                        event.getId(), event.getOriginalTopic(), event.getRetryCount());
            } else {
                handleRetryFailure(event);
            }

        } catch (Exception e) {
            log.error(" Error reprocessing event {}: {}", event.getId(), e.getMessage(), e);
            handleRetryFailure(event);
        }

        failedEventRepository.save(event);
    }

    @CircuitBreaker(name = "eventReprocessor", fallbackMethod = "reprocessEventFallback")
    private boolean reprocessEvent(FailedEvent event) throws Exception {
        log.debug("Attempting to reprocess event {} from topic {}",
                event.getId(), event.getOriginalTopic());

        switch (event.getOriginalTopic()) {
            case "order.paid", "order.paid.dlq" -> {
                JsonNode orderNode = objectMapper.readTree(event.getEventPayload());
                String orderIdStr = orderNode.get("id").asText();
                UUID orderUuid = UUID.fromString(orderIdStr);

                stockUpdateService.updateProductStock(orderUuid);
                return true;
            }
            case "product.sync", "product.sync.dlq" -> {
                ProductSyncEvent productEvent = objectMapper.readValue(
                        event.getEventPayload(),
                        ProductSyncEvent.class);
                productSyncService.processProductSyncEvent(productEvent);
                return true;
            }
            default -> {
                log.warn("Unknown topic for reprocessing: {}", event.getOriginalTopic());
                event.markAsFailed("Unknown topic: " + event.getOriginalTopic());
                return false;
            }
        }
    }

    @SuppressWarnings("unused") // Used by Resilience4j via reflection
    private boolean reprocessEventFallback(FailedEvent event, Exception ex) {
        log.warn("Circuit breaker OPEN for event reprocessor. Event {} will be retried later. Error: {}",
                event.getId(), ex.getMessage());
        return false;
    }

    private void handleRetryFailure(FailedEvent event) {
        event.incrementRetryCount();

        if (event.getRetryCount() >= event.getMaxRetries()) {
            log.error("🚨 Event {} reached maximum retries ({}). Manual intervention required.",
                    event.getId(), event.getMaxRetries());
            event.setStatus(FailedEvent.FailedEventStatus.MAX_RETRIES_REACHED);
            event.setProcessingNotes("Max retries reached. Manual intervention required.");
        } else {
            event.setStatus(FailedEvent.FailedEventStatus.PENDING);
            log.info("Event {} will be retried at {} (attempt {}/{})",
                    event.getId(), event.getNextRetryAt(),
                    event.getRetryCount() + 1, event.getMaxRetries());
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldProcessedEvents() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        int deleted = failedEventRepository.deleteOldProcessedEvents(cutoffDate);

        if (deleted > 0) {
            log.info("Cleaned up {} old processed events", deleted);
        }
    }

    @Scheduled(fixedDelay = 3600000)
    public void monitorStuckEvents() {
        LocalDateTime stuckThreshold = LocalDateTime.now().minusMinutes(30);
        List<FailedEvent> stuckEvents = failedEventRepository.findStuckRetryingEvents(stuckThreshold);

        if (!stuckEvents.isEmpty()) {
            log.error("Found {} events stuck in RETRYING state", stuckEvents.size());

            for (FailedEvent event : stuckEvents) {
                event.setStatus(FailedEvent.FailedEventStatus.PENDING);
                event.calculateNextRetryTime();
                failedEventRepository.save(event);
            }
        }
    }
}