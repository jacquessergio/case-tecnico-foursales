package com.foursales.eventconsumer.service;

import com.foursales.eventconsumer.dto.ProductSyncEvent;
import com.foursales.eventconsumer.entity.ProcessedEvent;
import com.foursales.eventconsumer.entity.Product;
import com.foursales.eventconsumer.repository.jpa.ProcessedEventRepository;
import com.foursales.eventconsumer.repository.jpa.ProductRepository;
import com.foursales.eventconsumer.repository.search.ProductElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSyncService {

    private final ProductRepository productRepository;
    private final ProductElasticsearchRepository productSearchRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    // ISOLATED TRANSACTION: Prevents race conditions when multiple events arrive for different products
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processProductSyncEvent(ProductSyncEvent event) {
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.warn("Event {} already processed. Skipping reprocessing.", event.getEventId());
            return;
        }

        try {
            switch (event.getEventType()) {
                case "CREATED", "UPDATED" -> syncProductToElasticsearch(event);
                case "DELETED" -> deleteProductFromElasticsearch(event);
                default -> {
                    log.warn("Unknown event type: {}", event.getEventType());
                    return;
                }
            }

            markEventAsProcessed(event, "SUCCESS", null);
            log.info("Successfully processed {} event for product: {}",
                    event.getEventType(), event.getProductId());

        } catch (Exception e) {
            log.error("Failed to process {} event for product: {}",
                    event.getEventType(), event.getProductId(), e);

            markEventAsProcessed(event, "FAILED", e.getMessage());
            throw new RuntimeException("Failed to sync product to Elasticsearch", e);
        }
    }

    // CRITICAL: Forces immediate refresh to ensure Elasticsearch document is searchable
    private void syncProductToElasticsearch(ProductSyncEvent event) {
        Product product = productRepository.findById(event.getProductId())
                .orElseThrow(() -> new RuntimeException(
                        "Product not found in MySQL: " + event.getProductId()));

        log.debug("Syncing product to Elasticsearch - ID: {}, Stock: {}",
                product.getId(), product.getStockQuantity());

        Product savedProduct = productSearchRepository.save(product);
        elasticsearchOperations.indexOps(Product.class).refresh();

        log.info("Product synchronized to Elasticsearch - ID: {}, Stock: {}, UpdatedAt: {}",
                savedProduct.getId(), savedProduct.getStockQuantity(), savedProduct.getUpdatedAt());
    }

    private void deleteProductFromElasticsearch(ProductSyncEvent event) {
        try {
            productSearchRepository.deleteById(event.getProductId());
            log.debug("Product deleted from Elasticsearch - ID: {}", event.getProductId());
        } catch (Exception e) {
            log.warn("Product {} not found in Elasticsearch for deletion", event.getProductId());
        }
    }

    private void markEventAsProcessed(ProductSyncEvent event, String status, String errorMessage) {
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .aggregateId(event.getProductId().toString())
                .processedAt(LocalDateTime.now())
                .status(status)
                .errorMessage(errorMessage != null ?
                        errorMessage.substring(0, Math.min(errorMessage.length(), 500)) : null)
                .build();

        processedEventRepository.save(processedEvent);
    }
}
