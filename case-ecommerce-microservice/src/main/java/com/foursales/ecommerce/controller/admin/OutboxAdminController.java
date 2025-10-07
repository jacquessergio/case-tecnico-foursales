package com.foursales.ecommerce.controller.admin;

import com.foursales.ecommerce.entity.OutboxEvent;
import com.foursales.ecommerce.outbox.OutboxService;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/outbox")
@RequiredArgsConstructor
@Tag(name = "Admin - Outbox", description = "Transactional Outbox monitoring and management")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
@Hidden
public class OutboxAdminController {

    private final OutboxService outboxService;

    @Operation(summary = "Get outbox statistics")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getOutboxStats() {
        Map<String, Object> stats = new HashMap<>();

        long unpublishedCount = outboxService.getUnpublishedCount();
        long failedCount = outboxService.getFailedCount(5);

        stats.put("unpublished", unpublishedCount);
        stats.put("failed", failedCount);
        stats.put("status", unpublishedCount < 100 ? "healthy" : "warning");

        if (unpublishedCount > 1000) {
            stats.put("alert", "High number of unpublished events. Kafka may be slow or down.");
        }

        if (failedCount > 0) {
            stats.put("warning", failedCount + " events have failed 5+ times. Manual intervention may be required.");
        }

        return ResponseEntity.ok(stats);
    }

    @Operation(summary = "List unpublished events")
    @GetMapping("/unpublished")
    public ResponseEntity<List<OutboxEvent>> getUnpublishedEvents() {
        List<OutboxEvent> events = outboxService.getUnpublishedEvents();
        return ResponseEntity.ok(events);
    }

    @Operation(summary = "List failed events")
    @GetMapping("/stuck")
    public ResponseEntity<List<OutboxEvent>> getStuckEvents() {
        List<OutboxEvent> events = outboxService.getStuckEvents(5);
        return ResponseEntity.ok(events);
    }

    @Operation(summary = "Retry event manually")
    @PostMapping("/retry/{eventId}")
    public ResponseEntity<Map<String, String>> retryEvent(@PathVariable Long eventId) {
        outboxService.manualRetry(eventId);

        log.info("Admin manually retried outbox event {}", eventId);

        return ResponseEntity.ok(Map.of(
                "message", "Event " + eventId + " reset for retry",
                "status", "success"));
    }

    @Operation(summary = "Check pending events for an aggregate")
    @GetMapping("/pending/{aggregateType}/{aggregateId}")
    public ResponseEntity<Map<String, Object>> checkPendingEvents(
            @PathVariable String aggregateType,
            @PathVariable String aggregateId) {

        boolean hasPending = outboxService.hasPendingEvents(aggregateType, aggregateId);

        Map<String, Object> response = new HashMap<>();
        response.put("aggregateType", aggregateType);
        response.put("aggregateId", aggregateId);
        response.put("hasPendingEvents", hasPending);

        if (hasPending) {
            response.put("warning", "There are unpublished events for this aggregate");
        }

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "List events by type")
    @GetMapping("/events/type/{eventType}")
    public ResponseEntity<List<OutboxEvent>> getEventsByType(
            @PathVariable String eventType,
            @RequestParam(defaultValue = "false") Boolean published) {

        List<OutboxEvent> events = outboxService.getEventsByType(eventType, published);
        return ResponseEntity.ok(events);
    }

    @Operation(summary = "Clean up old events manually")
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupOldEvents(
            @RequestParam(defaultValue = "7") int retentionDays) {

        long deletedCount = outboxService.cleanupOldEvents(retentionDays);

        log.info("Admin manually cleaned up {} old outbox events", deletedCount);

        return ResponseEntity.ok(Map.of(
                "deletedCount", deletedCount,
                "retentionDays", retentionDays,
                "message", "Cleaned up " + deletedCount + " old events"));
    }

    @Operation(summary = "List all events for an aggregate")
    @GetMapping("/events/{aggregateType}/{aggregateId}")
    public ResponseEntity<List<OutboxEvent>> getEventsForAggregate(
            @PathVariable String aggregateType,
            @PathVariable String aggregateId) {

        List<OutboxEvent> events = outboxService.getEventsForAggregate(aggregateType, aggregateId);
        return ResponseEntity.ok(events);
    }

    @Operation(summary = "Retry all failed events")
    @PostMapping("/retry-all")
    public ResponseEntity<Map<String, Object>> retryAllStuckEvents() {
        List<OutboxEvent> stuckEvents = outboxService.getStuckEvents(5);

        int retried = 0;
        for (OutboxEvent event : stuckEvents) {
            try {
                outboxService.manualRetry(event.getId());
                retried++;
            } catch (Exception e) {
                log.error("Failed to retry event {}: {}", event.getId(), e.getMessage());
            }
        }

        log.info("Admin retried {} stuck outbox events", retried);

        return ResponseEntity.ok(Map.of(
                "totalStuck", stuckEvents.size(),
                "retried", retried,
                "message", "Retried " + retried + " stuck events"));
    }
}
