package com.foursales.eventconsumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foursales.eventconsumer.dto.ProductSyncEvent;
import com.foursales.eventconsumer.entity.Order;
import com.foursales.eventconsumer.entity.OrderItem;
import com.foursales.eventconsumer.entity.Product;
import com.foursales.eventconsumer.exception.InsufficientStockException;
import com.foursales.eventconsumer.exception.OrderNotFoundException;
import com.foursales.eventconsumer.exception.StockUpdateException;
import com.foursales.eventconsumer.repository.jpa.OrderRepository;
import com.foursales.eventconsumer.repository.jpa.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class StockUpdateService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    // PESSIMISTIC LOCKING: Prevents race condition when concurrent orders reduce same product stock
    public void updateProductStock(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.isStockUpdated()) {
            log.warn("Stock already updated for order {}. Skipping reprocessing.", orderId);
            return;
        }

        for (OrderItem item : order.getItems()) {
            UUID productId = item.getProduct().getId();

            try {
                Product product = productRepository.findByIdForUpdate(productId)
                        .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

                log.info("Updating stock for product: {} - Current quantity: {} - Reducing: {}",
                    product.getId(), product.getStockQuantity(), item.getQuantity());

                product.reduceStock(item.getQuantity());
                Product savedProduct = productRepository.save(product);
                log.debug("Product saved to MySQL - New quantity: {}", savedProduct.getStockQuantity());

                publishProductSyncEvent(savedProduct);

                log.info("Stock updated successfully for product: {} - New quantity: {}",
                    product.getId(), savedProduct.getStockQuantity());

            } catch (InsufficientStockException e) {
                log.error("Insufficient stock for product {}: requested={}, available={}",
                    e.getProductId(), e.getRequested(), e.getAvailable());
                throw new StockUpdateException(orderId, e);
            } catch (Exception e) {
                log.error("Error updating stock for product: {}", productId, e);
                throw new StockUpdateException(orderId, e);
            }
        }

        order.markStockAsUpdated();
        orderRepository.save(order);

        log.info("Stock updated successfully for all products in order: {}", orderId);
    }

    private void publishProductSyncEvent(Product product) {
        try {
            ProductSyncEvent event = ProductSyncEvent.builder()
                    .productId(product.getId())
                    .eventType("UPDATED")
                    .eventTimestamp(LocalDateTime.now())
                    .eventId(UUID.randomUUID().toString())
                    .name(product.getName())
                    .description(product.getDescription())
                    .price(product.getPrice())
                    .category(product.getCategory())
                    .stockQuantity(product.getStockQuantity())
                    .createdAt(product.getCreatedAt())
                    .updatedAt(product.getUpdatedAt())
                    .build();

            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("product.sync", product.getId().toString(), eventJson);
            log.info("Published stock update event for product: {}", product.getId());
        } catch (Exception e) {
            log.error("Failed to publish stock update event for product: {}. Elasticsearch may be inconsistent.",
                    product.getId(), e);
        }
    }
}