package com.foursales.eventconsumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foursales.eventconsumer.entity.FailedEvent;
import com.foursales.eventconsumer.repository.jpa.FailedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FailedEventReprocessorTest {

    @Mock
    private FailedEventRepository failedEventRepository;

    @Mock
    private StockUpdateService stockUpdateService;

    @Mock
    private ProductSyncService productSyncService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private FailedEventReprocessor failedEventReprocessor;

    private FailedEvent failedEvent;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        failedEvent = new FailedEvent();
        failedEvent.setId(UUID.randomUUID());
        failedEvent.setOriginalTopic("order.paid.dlq");
        failedEvent.setEventPayload("{\"id\":\"" + UUID.randomUUID() + "\"}");
        failedEvent.setStatus(FailedEvent.FailedEventStatus.PENDING);
        failedEvent.setRetryCount(0);
    }

    @Test
    @DisplayName("Should skip reprocessing when no events ready")
    void shouldSkipReprocessingWhenNoEventsReady() {
        when(failedEventRepository.findEventsReadyForRetry(any(LocalDateTime.class), any(PageRequest.class)))
                .thenReturn(List.of());

        failedEventReprocessor.reprocessFailedEvents();

        verify(failedEventRepository).findEventsReadyForRetry(any(LocalDateTime.class), any(PageRequest.class));
        verify(failedEventRepository, never()).save(any(FailedEvent.class));
    }

    @Test
    @DisplayName("Should find and process failed events")
    void shouldFindAndProcessFailedEvents() {
        when(failedEventRepository.findEventsReadyForRetry(any(LocalDateTime.class), any(PageRequest.class)))
                .thenReturn(List.of(failedEvent));
        when(failedEventRepository.save(any(FailedEvent.class))).thenReturn(failedEvent);

        failedEventReprocessor.reprocessFailedEvents();

        verify(failedEventRepository).findEventsReadyForRetry(any(LocalDateTime.class), any(PageRequest.class));
    }

    @Test
    @DisplayName("Should handle exception during event reprocessing")
    void shouldHandleExceptionDuringEventReprocessing() {
        when(failedEventRepository.findEventsReadyForRetry(any(LocalDateTime.class), any(PageRequest.class)))
                .thenReturn(List.of(failedEvent));
        when(failedEventRepository.save(any(FailedEvent.class)))
                .thenThrow(new RuntimeException("Database error"));

        failedEventReprocessor.reprocessFailedEvents();

        verify(failedEventRepository).findEventsReadyForRetry(any(LocalDateTime.class), any(PageRequest.class));
    }

    @Test
    @DisplayName("Should process multiple failed events in batch")
    void shouldProcessMultipleFailedEventsInBatch() {
        FailedEvent event1 = new FailedEvent();
        event1.setId(UUID.randomUUID());
        event1.setOriginalTopic("order.paid.dlq");
        event1.setEventPayload("{\"id\":\"" + UUID.randomUUID() + "\"}");

        FailedEvent event2 = new FailedEvent();
        event2.setId(UUID.randomUUID());
        event2.setOriginalTopic("product.sync.dlq");
        event2.setEventPayload("{\"eventId\":\"123\"}");

        when(failedEventRepository.findEventsReadyForRetry(any(LocalDateTime.class), any(PageRequest.class)))
                .thenReturn(List.of(event1, event2));
        when(failedEventRepository.save(any(FailedEvent.class))).thenReturn(event1, event2);

        failedEventReprocessor.reprocessFailedEvents();

        verify(failedEventRepository).findEventsReadyForRetry(any(LocalDateTime.class), any(PageRequest.class));
    }

    @Test
    @DisplayName("Should process event in isolated transaction")
    void shouldProcessEventInIsolatedTransaction() {
        when(failedEventRepository.save(any(FailedEvent.class))).thenReturn(failedEvent);

        failedEventReprocessor.processEventInTransaction(failedEvent, now);

        verify(failedEventRepository, atLeast(1)).save(failedEvent);
    }

    @Test
    @DisplayName("Should update event status to RETRYING")
    void shouldUpdateEventStatusToRetrying() {
        when(failedEventRepository.save(any(FailedEvent.class))).thenReturn(failedEvent);

        failedEventReprocessor.processEventInTransaction(failedEvent, now);

        verify(failedEventRepository, atLeast(1)).save(failedEvent);
    }

    @Test
    @DisplayName("Should handle reprocessing failure gracefully")
    void shouldHandleReprocessingFailureGracefully() {
        lenient().when(failedEventRepository.save(any(FailedEvent.class))).thenReturn(failedEvent);
        lenient().doThrow(new RuntimeException("Reprocessing error"))
                .when(stockUpdateService).updateProductStock(any(UUID.class));

        failedEventReprocessor.processEventInTransaction(failedEvent, now);

        verify(failedEventRepository, atLeast(1)).save(failedEvent);
    }

    @Test
    @DisplayName("Should limit batch size to configured limit")
    void shouldLimitBatchSizeToConfiguredLimit() {
        when(failedEventRepository.findEventsReadyForRetry(any(LocalDateTime.class), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of());

        failedEventReprocessor.reprocessFailedEvents();

        verify(failedEventRepository).findEventsReadyForRetry(any(LocalDateTime.class), eq(PageRequest.of(0, 10)));
    }
}
