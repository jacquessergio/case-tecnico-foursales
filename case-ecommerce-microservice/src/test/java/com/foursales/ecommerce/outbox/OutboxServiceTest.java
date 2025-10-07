package com.foursales.ecommerce.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foursales.ecommerce.entity.OutboxEvent;
import com.foursales.ecommerce.repository.jpa.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OutboxService outboxService;

    private OutboxEvent outboxEvent;
    private String payload;

    @BeforeEach
    void setUp() {
        payload = "{\"id\":\"123\",\"name\":\"Test\"}";
        outboxEvent = OutboxEvent.builder()
                .id(1L)
                .aggregateType("ORDER")
                .aggregateId("test-id")
                .eventType("ORDER_PAID")
                .payload(payload)
                .topic("order.paid")
                .partitionKey("test-id")
                .createdAt(LocalDateTime.now())
                .published(false)
                .retryCount(0)
                .build();
    }

    @Test
    @DisplayName("Should save event with object payload successfully")
    void shouldSaveEventWithObjectPayloadSuccessfully() throws JsonProcessingException {
        Object testObject = new Object();
        when(objectMapper.writeValueAsString(testObject)).thenReturn(payload);
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(outboxEvent);

        OutboxEvent result = outboxService.saveEvent("ORDER", "test-id", "ORDER_PAID", testObject, "order.paid");

        assertThat(result).isNotNull();
        assertThat(result.getAggregateType()).isEqualTo("ORDER");
        assertThat(result.getEventType()).isEqualTo("ORDER_PAID");

        verify(objectMapper).writeValueAsString(testObject);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Should save event with string payload successfully")
    void shouldSaveEventWithStringPayloadSuccessfully() {
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(outboxEvent);

        OutboxEvent result = outboxService.saveEvent("ORDER", "test-id", "ORDER_PAID", payload, "order.paid");

        assertThat(result).isNotNull();
        assertThat(result.getPayload()).isEqualTo(payload);

        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Should throw RuntimeException when serialization fails")
    void shouldThrowRuntimeExceptionWhenSerializationFails() throws JsonProcessingException {
        Object testObject = new Object();
        when(objectMapper.writeValueAsString(testObject)).thenThrow(new JsonProcessingException("Serialization error") {});

        assertThatThrownBy(() -> outboxService.saveEvent("ORDER", "test-id", "ORDER_PAID", testObject, "order.paid"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to serialize event payload");

        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should get unpublished events")
    void shouldGetUnpublishedEvents() {
        when(outboxEventRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(outboxEvent));

        List<OutboxEvent> result = outboxService.getUnpublishedEvents();

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPublished()).isFalse();

        verify(outboxEventRepository).findTop100ByPublishedFalseOrderByCreatedAtAsc();
    }

    @Test
    @DisplayName("Should mark event as published")
    void shouldMarkEventAsPublished() {
        when(outboxEventRepository.save(outboxEvent)).thenReturn(outboxEvent);

        outboxService.markAsPublished(outboxEvent);

        verify(outboxEventRepository).save(outboxEvent);
    }

    @Test
    @DisplayName("Should record failure")
    void shouldRecordFailure() {
        String errorMessage = "Kafka connection failed";
        when(outboxEventRepository.save(outboxEvent)).thenReturn(outboxEvent);

        outboxService.recordFailure(outboxEvent, errorMessage);

        verify(outboxEventRepository).save(outboxEvent);
    }

    @Test
    @DisplayName("Should get unpublished count")
    void shouldGetUnpublishedCount() {
        when(outboxEventRepository.countByPublishedFalse()).thenReturn(5L);

        long count = outboxService.getUnpublishedCount();

        assertThat(count).isEqualTo(5L);

        verify(outboxEventRepository).countByPublishedFalse();
    }

    @Test
    @DisplayName("Should get failed count")
    void shouldGetFailedCount() {
        when(outboxEventRepository.countFailedEvents(3)).thenReturn(2L);

        long count = outboxService.getFailedCount(3);

        assertThat(count).isEqualTo(2L);

        verify(outboxEventRepository).countFailedEvents(3);
    }

    @Test
    @DisplayName("Should get stuck events")
    void shouldGetStuckEvents() {
        when(outboxEventRepository.findStuckEvents(5)).thenReturn(List.of(outboxEvent));

        List<OutboxEvent> result = outboxService.getStuckEvents(5);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);

        verify(outboxEventRepository).findStuckEvents(5);
    }

    @Test
    @DisplayName("Should check if has pending events")
    void shouldCheckIfHasPendingEvents() {
        when(outboxEventRepository.findByAggregateTypeAndAggregateIdAndPublishedFalse("ORDER", "test-id"))
                .thenReturn(List.of(outboxEvent));

        boolean hasPending = outboxService.hasPendingEvents("ORDER", "test-id");

        assertThat(hasPending).isTrue();

        verify(outboxEventRepository).findByAggregateTypeAndAggregateIdAndPublishedFalse("ORDER", "test-id");
    }

    @Test
    @DisplayName("Should return false when no pending events")
    void shouldReturnFalseWhenNoPendingEvents() {
        when(outboxEventRepository.findByAggregateTypeAndAggregateIdAndPublishedFalse("ORDER", "test-id"))
                .thenReturn(List.of());

        boolean hasPending = outboxService.hasPendingEvents("ORDER", "test-id");

        assertThat(hasPending).isFalse();

        verify(outboxEventRepository).findByAggregateTypeAndAggregateIdAndPublishedFalse("ORDER", "test-id");
    }

    @Test
    @DisplayName("Should cleanup old events")
    void shouldCleanupOldEvents() {
        when(outboxEventRepository.deleteByPublishedTrueAndPublishedAtBefore(any(LocalDateTime.class)))
                .thenReturn(10L);

        long deletedCount = outboxService.cleanupOldEvents(7);

        assertThat(deletedCount).isEqualTo(10L);

        verify(outboxEventRepository).deleteByPublishedTrueAndPublishedAtBefore(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Should get events by type")
    void shouldGetEventsByType() {
        when(outboxEventRepository.findByEventTypeAndPublished("ORDER_PAID", false))
                .thenReturn(List.of(outboxEvent));

        List<OutboxEvent> result = outboxService.getEventsByType("ORDER_PAID", false);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);

        verify(outboxEventRepository).findByEventTypeAndPublished("ORDER_PAID", false);
    }

    @Test
    @DisplayName("Should manually retry event")
    void shouldManuallyRetryEvent() {
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(outboxEvent));
        when(outboxEventRepository.save(outboxEvent)).thenReturn(outboxEvent);

        outboxService.manualRetry(1L);

        verify(outboxEventRepository).findById(1L);
        verify(outboxEventRepository).save(outboxEvent);
    }

    @Test
    @DisplayName("Should throw RuntimeException when event not found for manual retry")
    void shouldThrowRuntimeExceptionWhenEventNotFoundForManualRetry() {
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> outboxService.manualRetry(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Event not found");

        verify(outboxEventRepository).findById(1L);
        verify(outboxEventRepository, never()).save(any());
    }
}
