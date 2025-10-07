package com.foursales.eventconsumer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foursales.eventconsumer.service.StockUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventConsumerTest {

    @Mock
    private StockUpdateService stockUpdateService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private OrderEventConsumer orderEventConsumer;

    private UUID orderId;
    private String validOrderJson;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        validOrderJson = String.format("{\"id\":\"%s\",\"status\":\"PAGO\"}", orderId.toString());
    }

    @Test
    @DisplayName("Should successfully process order paid event")
    void shouldSuccessfullyProcessOrderPaidEvent() throws Exception {
        when(objectMapper.readTree(validOrderJson))
                .thenReturn(new ObjectMapper().readTree(validOrderJson));

        orderEventConsumer.handleOrderPaid(validOrderJson, 0, 100L, acknowledgment);

        verify(stockUpdateService).updateProductStock(orderId);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should call stockUpdateService with correct order ID")
    void shouldCallStockUpdateServiceWithCorrectOrderId() throws Exception {
        when(objectMapper.readTree(validOrderJson))
                .thenReturn(new ObjectMapper().readTree(validOrderJson));

        orderEventConsumer.handleOrderPaid(validOrderJson, 0, 100L, acknowledgment);

        verify(stockUpdateService).updateProductStock(orderId);
    }

    @Test
    @DisplayName("Should acknowledge message after successful processing")
    void shouldAcknowledgeMessageAfterSuccessfulProcessing() throws Exception {
        when(objectMapper.readTree(validOrderJson))
                .thenReturn(new ObjectMapper().readTree(validOrderJson));

        orderEventConsumer.handleOrderPaid(validOrderJson, 0, 100L, acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should throw exception when JSON parsing fails")
    void shouldThrowExceptionWhenJsonParsingFails() throws Exception {
        when(objectMapper.readTree(anyString()))
                .thenThrow(new RuntimeException("Invalid JSON"));

        assertThrows(RuntimeException.class, () ->
                orderEventConsumer.handleOrderPaid("invalid-json", 0, 100L, acknowledgment)
        );

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should throw exception when stock update fails")
    void shouldThrowExceptionWhenStockUpdateFails() throws Exception {
        when(objectMapper.readTree(validOrderJson))
                .thenReturn(new ObjectMapper().readTree(validOrderJson));
        doThrow(new RuntimeException("Stock update failed"))
                .when(stockUpdateService).updateProductStock(any(UUID.class));

        assertThrows(RuntimeException.class, () ->
                orderEventConsumer.handleOrderPaid(validOrderJson, 0, 100L, acknowledgment)
        );

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should not acknowledge when exception occurs")
    void shouldNotAcknowledgeWhenExceptionOccurs() throws Exception {
        when(objectMapper.readTree(anyString()))
                .thenThrow(new RuntimeException("Processing error"));

        assertThrows(RuntimeException.class, () ->
                orderEventConsumer.handleOrderPaid(validOrderJson, 0, 100L, acknowledgment)
        );

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    @DisplayName("Should handle different partition and offset values")
    void shouldHandleDifferentPartitionAndOffsetValues() throws Exception {
        when(objectMapper.readTree(validOrderJson))
                .thenReturn(new ObjectMapper().readTree(validOrderJson));

        orderEventConsumer.handleOrderPaid(validOrderJson, 5, 999L, acknowledgment);

        verify(stockUpdateService).updateProductStock(orderId);
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should process multiple events sequentially")
    void shouldProcessMultipleEventsSequentially() throws Exception {
        UUID orderId2 = UUID.randomUUID();
        String orderJson2 = String.format("{\"id\":\"%s\",\"status\":\"PAGO\"}", orderId2.toString());

        when(objectMapper.readTree(validOrderJson))
                .thenReturn(new ObjectMapper().readTree(validOrderJson));
        when(objectMapper.readTree(orderJson2))
                .thenReturn(new ObjectMapper().readTree(orderJson2));

        orderEventConsumer.handleOrderPaid(validOrderJson, 0, 100L, acknowledgment);
        orderEventConsumer.handleOrderPaid(orderJson2, 0, 101L, acknowledgment);

        verify(stockUpdateService).updateProductStock(orderId);
        verify(stockUpdateService).updateProductStock(orderId2);
        verify(acknowledgment, times(2)).acknowledge();
    }
}
