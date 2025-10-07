package com.foursales.eventconsumer.kafka;

import com.foursales.eventconsumer.entity.FailedEvent;
import com.foursales.eventconsumer.repository.jpa.FailedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeadLetterQueueConsumerTest {

    @Mock
    private FailedEventRepository failedEventRepository;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private DeadLetterQueueConsumer deadLetterQueueConsumer;

    private String orderJson;
    private String productJson;
    private String exceptionMessage;
    private String stackTrace;

    @BeforeEach
    void setUp() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        orderJson = String.format("{\"id\":\"%s\",\"status\":\"PAGO\"}", orderId.toString());
        productJson = String.format("{\"productId\":\"%s\",\"eventType\":\"CREATED\"}", productId.toString());
        exceptionMessage = "Test exception message";
        stackTrace = "java.lang.RuntimeException: Test exception\n\tat TestClass.testMethod(TestClass.java:100)";
    }

    @Test
    @DisplayName("Should successfully handle order paid DLQ event")
    void shouldSuccessfullyHandleOrderPaidDlqEvent() {
        ArgumentCaptor<FailedEvent> failedEventCaptor = ArgumentCaptor.forClass(FailedEvent.class);

        deadLetterQueueConsumer.handleOrderPaidDLQ(
                orderJson, "order-key", exceptionMessage, stackTrace, 0, 100L, acknowledgment
        );

        verify(failedEventRepository).save(failedEventCaptor.capture());
        verify(acknowledgment).acknowledge();

        FailedEvent capturedEvent = failedEventCaptor.getValue();
        assertEquals("order.paid", capturedEvent.getOriginalTopic());
        assertEquals(orderJson, capturedEvent.getEventPayload());
        assertEquals(exceptionMessage, capturedEvent.getExceptionMessage());
        assertEquals(FailedEvent.FailedEventStatus.PENDING, capturedEvent.getStatus());
        assertEquals(0, capturedEvent.getRetryCount());
        assertNotNull(capturedEvent.getNextRetryAt());
    }

    @Test
    @DisplayName("Should successfully handle product sync DLQ event")
    void shouldSuccessfullyHandleProductSyncDlqEvent() {
        ArgumentCaptor<FailedEvent> failedEventCaptor = ArgumentCaptor.forClass(FailedEvent.class);

        deadLetterQueueConsumer.handleProductSyncDLQ(
                productJson, "product-key", exceptionMessage, stackTrace, 0, 100L, acknowledgment
        );

        verify(failedEventRepository).save(failedEventCaptor.capture());
        verify(acknowledgment).acknowledge();

        FailedEvent capturedEvent = failedEventCaptor.getValue();
        assertEquals("product.sync", capturedEvent.getOriginalTopic());
        assertEquals(productJson, capturedEvent.getEventPayload());
        assertEquals(exceptionMessage, capturedEvent.getExceptionMessage());
        assertEquals(FailedEvent.FailedEventStatus.PENDING, capturedEvent.getStatus());
        assertEquals(0, capturedEvent.getRetryCount());
        assertNotNull(capturedEvent.getNextRetryAt());
    }

    @Test
    @DisplayName("Should store event with correct initial values for order DLQ")
    void shouldStoreEventWithCorrectInitialValuesForOrderDlq() {
        ArgumentCaptor<FailedEvent> failedEventCaptor = ArgumentCaptor.forClass(FailedEvent.class);

        deadLetterQueueConsumer.handleOrderPaidDLQ(
                orderJson, "test-key", exceptionMessage, stackTrace, 0, 100L, acknowledgment
        );

        verify(failedEventRepository).save(failedEventCaptor.capture());

        FailedEvent capturedEvent = failedEventCaptor.getValue();
        assertEquals("order.paid", capturedEvent.getOriginalTopic());
        assertEquals("test-key", capturedEvent.getEventKey());
        assertEquals(0, capturedEvent.getRetryCount());
        assertEquals(10, capturedEvent.getMaxRetries());
        assertEquals(FailedEvent.FailedEventStatus.PENDING, capturedEvent.getStatus());
    }

    @Test
    @DisplayName("Should truncate long exception message")
    void shouldTruncateLongExceptionMessage() {
        String longMessage = "x".repeat(1500);
        ArgumentCaptor<FailedEvent> failedEventCaptor = ArgumentCaptor.forClass(FailedEvent.class);

        deadLetterQueueConsumer.handleOrderPaidDLQ(
                orderJson, "key", longMessage, stackTrace, 0, 100L, acknowledgment
        );

        verify(failedEventRepository).save(failedEventCaptor.capture());

        FailedEvent capturedEvent = failedEventCaptor.getValue();
        assertEquals(1000, capturedEvent.getExceptionMessage().length());
        assertTrue(capturedEvent.getExceptionMessage().endsWith("..."));
    }

    @Test
    @DisplayName("Should truncate long stack trace")
    void shouldTruncateLongStackTrace() {
        String longStackTrace = "x".repeat(6000);
        ArgumentCaptor<FailedEvent> failedEventCaptor = ArgumentCaptor.forClass(FailedEvent.class);

        deadLetterQueueConsumer.handleOrderPaidDLQ(
                orderJson, "key", exceptionMessage, longStackTrace, 0, 100L, acknowledgment
        );

        verify(failedEventRepository).save(failedEventCaptor.capture());

        FailedEvent capturedEvent = failedEventCaptor.getValue();
        assertEquals(5000, capturedEvent.getStackTrace().length());
        assertTrue(capturedEvent.getStackTrace().endsWith("..."));
    }

    @Test
    @DisplayName("Should handle null exception message")
    void shouldHandleNullExceptionMessage() {
        ArgumentCaptor<FailedEvent> failedEventCaptor = ArgumentCaptor.forClass(FailedEvent.class);

        deadLetterQueueConsumer.handleOrderPaidDLQ(
                orderJson, "key", null, stackTrace, 0, 100L, acknowledgment
        );

        verify(failedEventRepository).save(failedEventCaptor.capture());

        FailedEvent capturedEvent = failedEventCaptor.getValue();
        assertNull(capturedEvent.getExceptionMessage());
    }

    @Test
    @DisplayName("Should handle null stack trace")
    void shouldHandleNullStackTrace() {
        ArgumentCaptor<FailedEvent> failedEventCaptor = ArgumentCaptor.forClass(FailedEvent.class);

        deadLetterQueueConsumer.handleOrderPaidDLQ(
                orderJson, "key", exceptionMessage, null, 0, 100L, acknowledgment
        );

        verify(failedEventRepository).save(failedEventCaptor.capture());

        FailedEvent capturedEvent = failedEventCaptor.getValue();
        assertNull(capturedEvent.getStackTrace());
    }

    @Test
    @DisplayName("Should handle null event key")
    void shouldHandleNullEventKey() {
        ArgumentCaptor<FailedEvent> failedEventCaptor = ArgumentCaptor.forClass(FailedEvent.class);

        deadLetterQueueConsumer.handleOrderPaidDLQ(
                orderJson, null, exceptionMessage, stackTrace, 0, 100L, acknowledgment
        );

        verify(failedEventRepository).save(failedEventCaptor.capture());

        FailedEvent capturedEvent = failedEventCaptor.getValue();
        assertNull(capturedEvent.getEventKey());
    }

    @Test
    @DisplayName("Should acknowledge after successfully storing order DLQ event")
    void shouldAcknowledgeAfterSuccessfullyStoringOrderDlqEvent() {
        deadLetterQueueConsumer.handleOrderPaidDLQ(
                orderJson, "key", exceptionMessage, stackTrace, 0, 100L, acknowledgment
        );

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should acknowledge after successfully storing product DLQ event")
    void shouldAcknowledgeAfterSuccessfullyStoringProductDlqEvent() {
        deadLetterQueueConsumer.handleProductSyncDLQ(
                productJson, "key", exceptionMessage, stackTrace, 0, 100L, acknowledgment
        );

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should throw exception when repository save fails for order DLQ")
    void shouldThrowExceptionWhenRepositorySaveFailsForOrderDlq() {
        when(failedEventRepository.save(any(FailedEvent.class)))
                .thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () ->
                deadLetterQueueConsumer.handleOrderPaidDLQ(
                        orderJson, "key", exceptionMessage, stackTrace, 0, 100L, acknowledgment
                )
        );

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should throw exception when repository save fails for product DLQ")
    void shouldThrowExceptionWhenRepositorySaveFailsForProductDlq() {
        when(failedEventRepository.save(any(FailedEvent.class)))
                .thenThrow(new RuntimeException("Database error"));

        assertThrows(RuntimeException.class, () ->
                deadLetterQueueConsumer.handleProductSyncDLQ(
                        productJson, "key", exceptionMessage, stackTrace, 0, 100L, acknowledgment
                )
        );

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should not acknowledge when exception occurs in order DLQ")
    void shouldNotAcknowledgeWhenExceptionOccursInOrderDlq() {
        when(failedEventRepository.save(any(FailedEvent.class)))
                .thenThrow(new RuntimeException("Error"));

        assertThrows(RuntimeException.class, () ->
                deadLetterQueueConsumer.handleOrderPaidDLQ(
                        orderJson, "key", exceptionMessage, stackTrace, 0, 100L, acknowledgment
                )
        );

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should handle different partition and offset values")
    void shouldHandleDifferentPartitionAndOffsetValues() {
        deadLetterQueueConsumer.handleOrderPaidDLQ(
                orderJson, "key", exceptionMessage, stackTrace, 5, 999L, acknowledgment
        );

        verify(failedEventRepository).save(any(FailedEvent.class));
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should calculate next retry time when storing event")
    void shouldCalculateNextRetryTimeWhenStoringEvent() {
        ArgumentCaptor<FailedEvent> failedEventCaptor = ArgumentCaptor.forClass(FailedEvent.class);

        deadLetterQueueConsumer.handleOrderPaidDLQ(
                orderJson, "key", exceptionMessage, stackTrace, 0, 100L, acknowledgment
        );

        verify(failedEventRepository).save(failedEventCaptor.capture());

        FailedEvent capturedEvent = failedEventCaptor.getValue();
        assertNotNull(capturedEvent.getNextRetryAt());
    }
}
