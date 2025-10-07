package com.foursales.eventconsumer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foursales.eventconsumer.dto.ProductSyncEvent;
import com.foursales.eventconsumer.service.ProductSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductSyncEventConsumerTest {

    @Mock
    private ProductSyncService productSyncService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private ProductSyncEventConsumer productSyncEventConsumer;

    private ProductSyncEvent productSyncEvent;
    private String productSyncEventJson;
    private UUID productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        productSyncEvent = ProductSyncEvent.builder()
                .productId(productId)
                .eventType("CREATED")
                .eventId(UUID.randomUUID().toString())
                .eventTimestamp(LocalDateTime.now())
                .name("Test Product")
                .description("Test Description")
                .price(new BigDecimal("99.99"))
                .category("Electronics")
                .stockQuantity(10)
                .build();

        productSyncEventJson = "{\"productId\":\"" + productId + "\",\"eventType\":\"CREATED\"}";
    }

    @Test
    @DisplayName("Should successfully consume product sync event")
    void shouldSuccessfullyConsumeProductSyncEvent() throws Exception {
        when(objectMapper.readValue(productSyncEventJson, ProductSyncEvent.class))
                .thenReturn(productSyncEvent);

        productSyncEventConsumer.consumeProductSyncEvent(
                productSyncEventJson, productId.toString(), 0, 100L, acknowledgment
        );

        verify(productSyncService).processProductSyncEvent(productSyncEvent);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should call productSyncService with correct event")
    void shouldCallProductSyncServiceWithCorrectEvent() throws Exception {
        when(objectMapper.readValue(productSyncEventJson, ProductSyncEvent.class))
                .thenReturn(productSyncEvent);

        productSyncEventConsumer.consumeProductSyncEvent(
                productSyncEventJson, productId.toString(), 0, 100L, acknowledgment
        );

        verify(productSyncService).processProductSyncEvent(productSyncEvent);
    }

    @Test
    @DisplayName("Should acknowledge message after successful processing")
    void shouldAcknowledgeMessageAfterSuccessfulProcessing() throws Exception {
        when(objectMapper.readValue(productSyncEventJson, ProductSyncEvent.class))
                .thenReturn(productSyncEvent);

        productSyncEventConsumer.consumeProductSyncEvent(
                productSyncEventJson, productId.toString(), 0, 100L, acknowledgment
        );

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should throw exception when JSON parsing fails")
    void shouldThrowExceptionWhenJsonParsingFails() throws Exception {
        when(objectMapper.readValue(anyString(), eq(ProductSyncEvent.class)))
                .thenThrow(new RuntimeException("Invalid JSON"));

        assertThrows(RuntimeException.class, () ->
                productSyncEventConsumer.consumeProductSyncEvent(
                        "invalid-json", productId.toString(), 0, 100L, acknowledgment
                )
        );

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should throw exception when sync service fails")
    void shouldThrowExceptionWhenSyncServiceFails() throws Exception {
        when(objectMapper.readValue(productSyncEventJson, ProductSyncEvent.class))
                .thenReturn(productSyncEvent);
        doThrow(new RuntimeException("Sync failed"))
                .when(productSyncService).processProductSyncEvent(any(ProductSyncEvent.class));

        assertThrows(RuntimeException.class, () ->
                productSyncEventConsumer.consumeProductSyncEvent(
                        productSyncEventJson, productId.toString(), 0, 100L, acknowledgment
                )
        );

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should not acknowledge when exception occurs")
    void shouldNotAcknowledgeWhenExceptionOccurs() throws Exception {
        when(objectMapper.readValue(anyString(), eq(ProductSyncEvent.class)))
                .thenThrow(new RuntimeException("Processing error"));

        assertThrows(RuntimeException.class, () ->
                productSyncEventConsumer.consumeProductSyncEvent(
                        productSyncEventJson, productId.toString(), 0, 100L, acknowledgment
                )
        );

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should handle UPDATED event type")
    void shouldHandleUpdatedEventType() throws Exception {
        productSyncEvent.setEventType("UPDATED");
        when(objectMapper.readValue(productSyncEventJson, ProductSyncEvent.class))
                .thenReturn(productSyncEvent);

        productSyncEventConsumer.consumeProductSyncEvent(
                productSyncEventJson, productId.toString(), 0, 100L, acknowledgment
        );

        verify(productSyncService).processProductSyncEvent(productSyncEvent);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle DELETED event type")
    void shouldHandleDeletedEventType() throws Exception {
        productSyncEvent.setEventType("DELETED");
        when(objectMapper.readValue(productSyncEventJson, ProductSyncEvent.class))
                .thenReturn(productSyncEvent);

        productSyncEventConsumer.consumeProductSyncEvent(
                productSyncEventJson, productId.toString(), 0, 100L, acknowledgment
        );

        verify(productSyncService).processProductSyncEvent(productSyncEvent);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle different partition and offset values")
    void shouldHandleDifferentPartitionAndOffsetValues() throws Exception {
        when(objectMapper.readValue(productSyncEventJson, ProductSyncEvent.class))
                .thenReturn(productSyncEvent);

        productSyncEventConsumer.consumeProductSyncEvent(
                productSyncEventJson, productId.toString(), 3, 500L, acknowledgment
        );

        verify(productSyncService).processProductSyncEvent(productSyncEvent);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should process multiple events sequentially")
    void shouldProcessMultipleEventsSequentially() throws Exception {
        ProductSyncEvent event2 = ProductSyncEvent.builder()
                .productId(UUID.randomUUID())
                .eventType("UPDATED")
                .eventId(UUID.randomUUID().toString())
                .eventTimestamp(LocalDateTime.now())
                .build();

        String event2Json = "{\"productId\":\"" + event2.getProductId() + "\",\"eventType\":\"UPDATED\"}";

        when(objectMapper.readValue(productSyncEventJson, ProductSyncEvent.class))
                .thenReturn(productSyncEvent);
        when(objectMapper.readValue(event2Json, ProductSyncEvent.class))
                .thenReturn(event2);

        productSyncEventConsumer.consumeProductSyncEvent(
                productSyncEventJson, productId.toString(), 0, 100L, acknowledgment
        );
        productSyncEventConsumer.consumeProductSyncEvent(
                event2Json, event2.getProductId().toString(), 0, 101L, acknowledgment
        );

        verify(productSyncService).processProductSyncEvent(productSyncEvent);
        verify(productSyncService).processProductSyncEvent(event2);
        verify(acknowledgment, times(2)).acknowledge();
    }
}
