package com.foursales.eventconsumer.service;

import com.foursales.eventconsumer.dto.ProductSyncEvent;
import com.foursales.eventconsumer.entity.ProcessedEvent;
import com.foursales.eventconsumer.entity.Product;
import com.foursales.eventconsumer.repository.jpa.ProcessedEventRepository;
import com.foursales.eventconsumer.repository.jpa.ProductRepository;
import com.foursales.eventconsumer.repository.search.ProductElasticsearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductSyncServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductElasticsearchRepository productSearchRepository;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private IndexOperations indexOperations;

    @InjectMocks
    private ProductSyncService productSyncService;

    private ProductSyncEvent productSyncEvent;
    private Product product;
    private UUID productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();

        product = new Product();
        product.setId(productId);
        product.setName("Test Product");
        product.setDescription("Description");
        product.setPrice(new BigDecimal("100.00"));
        product.setCategory("Electronics");
        product.setStockQuantity(10);

        productSyncEvent = ProductSyncEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .productId(productId)
                .eventType("CREATED")
                .eventTimestamp(LocalDateTime.now())
                .name("Test Product")
                .description("Description")
                .price(new BigDecimal("100.00"))
                .category("Electronics")
                .stockQuantity(10)
                .build();
    }

    @Test
    @DisplayName("Should process CREATED event successfully")
    void shouldProcessCreatedEventSuccessfully() {
        when(processedEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productSearchRepository.save(any(Product.class))).thenReturn(product);
        when(elasticsearchOperations.indexOps(Product.class)).thenReturn(indexOperations);
        doNothing().when(indexOperations).refresh();
        when(processedEventRepository.save(any(ProcessedEvent.class))).thenReturn(new ProcessedEvent());

        productSyncService.processProductSyncEvent(productSyncEvent);

        verify(processedEventRepository).existsByEventId(anyString());
        verify(productRepository).findById(productId);
        verify(productSearchRepository).save(any(Product.class));
        verify(elasticsearchOperations).indexOps(Product.class);
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("Should process UPDATED event successfully")
    void shouldProcessUpdatedEventSuccessfully() {
        productSyncEvent.setEventType("UPDATED");

        when(processedEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productSearchRepository.save(any(Product.class))).thenReturn(product);
        when(elasticsearchOperations.indexOps(Product.class)).thenReturn(indexOperations);
        doNothing().when(indexOperations).refresh();
        when(processedEventRepository.save(any(ProcessedEvent.class))).thenReturn(new ProcessedEvent());

        productSyncService.processProductSyncEvent(productSyncEvent);

        verify(productRepository).findById(productId);
        verify(productSearchRepository).save(any(Product.class));
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("Should process DELETED event successfully")
    void shouldProcessDeletedEventSuccessfully() {
        productSyncEvent.setEventType("DELETED");

        when(processedEventRepository.existsByEventId(anyString())).thenReturn(false);
        doNothing().when(productSearchRepository).deleteById(productId);
        when(processedEventRepository.save(any(ProcessedEvent.class))).thenReturn(new ProcessedEvent());

        productSyncService.processProductSyncEvent(productSyncEvent);

        verify(productSearchRepository).deleteById(productId);
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
        verify(productRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Should skip processing when event already processed")
    void shouldSkipProcessingWhenEventAlreadyProcessed() {
        when(processedEventRepository.existsByEventId(anyString())).thenReturn(true);

        productSyncService.processProductSyncEvent(productSyncEvent);

        verify(processedEventRepository).existsByEventId(anyString());
        verify(productRepository, never()).findById(any());
        verify(productSearchRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw RuntimeException when product not found in MySQL")
    void shouldThrowRuntimeExceptionWhenProductNotFoundInMySQL() {
        when(processedEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productSyncService.processProductSyncEvent(productSyncEvent))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to sync product to Elasticsearch");

        verify(productRepository).findById(productId);
        verify(productSearchRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should mark event as FAILED when exception occurs")
    void shouldMarkEventAsFailedWhenExceptionOccurs() {
        when(processedEventRepository.existsByEventId(anyString())).thenReturn(false);
        when(productRepository.findById(productId)).thenThrow(new RuntimeException("Database error"));
        when(processedEventRepository.save(any(ProcessedEvent.class))).thenReturn(new ProcessedEvent());

        assertThatThrownBy(() -> productSyncService.processProductSyncEvent(productSyncEvent))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to sync product to Elasticsearch");

        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("Should handle unknown event type")
    void shouldHandleUnknownEventType() {
        productSyncEvent.setEventType("UNKNOWN");

        when(processedEventRepository.existsByEventId(anyString())).thenReturn(false);

        productSyncService.processProductSyncEvent(productSyncEvent);

        verify(productRepository, never()).findById(any());
        verify(productSearchRepository, never()).save(any());
        verify(productSearchRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Should handle deletion of non-existent product gracefully")
    void shouldHandleDeletionOfNonExistentProductGracefully() {
        productSyncEvent.setEventType("DELETED");

        when(processedEventRepository.existsByEventId(anyString())).thenReturn(false);
        doThrow(new RuntimeException("Product not found")).when(productSearchRepository).deleteById(productId);
        when(processedEventRepository.save(any(ProcessedEvent.class))).thenReturn(new ProcessedEvent());

        productSyncService.processProductSyncEvent(productSyncEvent);

        verify(productSearchRepository).deleteById(productId);
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
    }
}
