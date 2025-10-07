package com.foursales.eventconsumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foursales.eventconsumer.entity.Order;
import com.foursales.eventconsumer.entity.OrderItem;
import com.foursales.eventconsumer.entity.Product;
import com.foursales.eventconsumer.exception.OrderNotFoundException;
import com.foursales.eventconsumer.exception.StockUpdateException;
import com.foursales.eventconsumer.repository.jpa.OrderRepository;
import com.foursales.eventconsumer.repository.jpa.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockUpdateServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private StockUpdateService stockUpdateService;

    private UUID orderId;
    private Order order;
    private Product product;
    private OrderItem orderItem;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();

        product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Test Product");
        product.setDescription("Description");
        product.setPrice(new BigDecimal("100.00"));
        product.setCategory("Electronics");
        product.setStockQuantity(10);

        order = new Order();
        orderItem = new OrderItem();
        orderItem.setProduct(product);
        orderItem.setQuantity(2);

        List<OrderItem> items = new ArrayList<>();
        items.add(orderItem);
        order.setItems(items);
        order.setStockUpdated(false);
    }

    @Test
    @DisplayName("Should update stock successfully")
    void shouldUpdateStockSuccessfully() throws Exception {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(any(UUID.class))).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        stockUpdateService.updateProductStock(orderId);

        verify(orderRepository).findById(orderId);
        verify(productRepository).findByIdForUpdate(any(UUID.class));
        verify(productRepository).save(any(Product.class));
        verify(orderRepository).save(order);
        verify(kafkaTemplate).send(eq("product.sync"), anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw OrderNotFoundException when order not found")
    void shouldThrowOrderNotFoundExceptionWhenOrderNotFound() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockUpdateService.updateProductStock(orderId))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository).findById(orderId);
        verify(productRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("Should skip processing when stock already updated")
    void shouldSkipProcessingWhenStockAlreadyUpdated() {
        order.setStockUpdated(true);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        stockUpdateService.updateProductStock(orderId);

        verify(orderRepository).findById(orderId);
        verify(productRepository, never()).findByIdForUpdate(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw StockUpdateException when insufficient stock")
    void shouldThrowStockUpdateExceptionWhenInsufficientStock() {
        Product lowStockProduct = new Product();
        lowStockProduct.setId(UUID.randomUUID());
        lowStockProduct.setName("Test Product");
        lowStockProduct.setDescription("Description");
        lowStockProduct.setPrice(new BigDecimal("100.00"));
        lowStockProduct.setCategory("Electronics");
        lowStockProduct.setStockQuantity(1);
        orderItem.setProduct(lowStockProduct);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(any(UUID.class))).thenReturn(Optional.of(lowStockProduct));

        assertThatThrownBy(() -> stockUpdateService.updateProductStock(orderId))
                .isInstanceOf(StockUpdateException.class);

        verify(orderRepository).findById(orderId);
        verify(productRepository).findByIdForUpdate(any(UUID.class));
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw StockUpdateException when product not found")
    void shouldThrowStockUpdateExceptionWhenProductNotFound() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockUpdateService.updateProductStock(orderId))
                .isInstanceOf(StockUpdateException.class);

        verify(orderRepository).findById(orderId);
        verify(productRepository).findByIdForUpdate(any(UUID.class));
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle Kafka publish failure gracefully")
    void shouldHandleKafkaPublishFailureGracefully() throws Exception {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(productRepository.findByIdForUpdate(any(UUID.class))).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("Kafka error"));

        stockUpdateService.updateProductStock(orderId);

        verify(orderRepository).findById(orderId);
        verify(productRepository).save(any(Product.class));
        verify(orderRepository).save(order);
    }
}
