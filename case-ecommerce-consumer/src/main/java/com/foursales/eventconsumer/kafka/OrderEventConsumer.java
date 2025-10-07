package com.foursales.eventconsumer.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foursales.eventconsumer.service.StockUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final StockUpdateService stockUpdateService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.paid", groupId = "ecommerce-stock-group", containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderPaid(
            @Payload String orderJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        try {
            log.info("Received payment event - Partition: {}, Offset: {}", partition, offset);

            JsonNode orderNode = objectMapper.readTree(orderJson);
            String orderIdStr = orderNode.get("id").asText();
            UUID orderUuid = UUID.fromString(orderIdStr);

            stockUpdateService.updateProductStock(orderUuid);
            acknowledgment.acknowledge();

            log.info("Successfully processed order: {} - Partition: {}, Offset: {}",
                    orderIdStr, partition, offset);

        } catch (Exception e) {
            log.error("Failed to process payment event - Partition: {}, Offset: {}, Order: {}",
                    partition, offset, orderJson, e);
            throw new RuntimeException("Failed to process order payment event", e);
        }
    }
}
