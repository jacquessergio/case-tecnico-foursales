package com.foursales.eventconsumer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foursales.eventconsumer.dto.ProductSyncEvent;
import com.foursales.eventconsumer.service.ProductSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductSyncEventConsumer {

        private final ProductSyncService productSyncService;
        private final ObjectMapper objectMapper;

        @KafkaListener(topics = "product.sync", groupId = "product-sync-consumer-group", containerFactory = "kafkaListenerContainerFactory")
        public void consumeProductSyncEvent(
                        @Payload String payload,
                        @Header(KafkaHeaders.RECEIVED_KEY) String key,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset,
                        Acknowledgment acknowledgment) {

                try {
                        log.info("Received product sync event - Key: {}, Partition: {}, Offset: {}",
                                        key, partition, offset);

                        ProductSyncEvent event = objectMapper.readValue(payload, ProductSyncEvent.class);

                        log.debug("Processing {} event for product: {}", event.getEventType(), event.getProductId());

                        productSyncService.processProductSyncEvent(event);
                        acknowledgment.acknowledge();

                        log.info("Successfully consumed and acknowledged product sync event - ProductId: {}, EventType: {}",
                                        event.getProductId(), event.getEventType());

                } catch (Exception e) {
                        log.error("Error consuming product sync event - Key: {}, Partition: {}, Offset: {}",
                                        key, partition, offset, e);
                        throw new RuntimeException("Failed to consume product sync event", e);
                }
        }
}
