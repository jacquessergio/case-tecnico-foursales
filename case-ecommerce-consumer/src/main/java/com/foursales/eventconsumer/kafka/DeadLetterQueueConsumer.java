package com.foursales.eventconsumer.kafka;

import com.foursales.eventconsumer.entity.FailedEvent;
import com.foursales.eventconsumer.repository.jpa.FailedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeadLetterQueueConsumer {

    private final FailedEventRepository failedEventRepository;

    @KafkaListener(topics = "order.paid.dlq", groupId = "ecommerce-dlq-group", containerFactory = "kafkaListenerContainerFactory")
    public void handleOrderPaidDLQ(
            @Payload String orderJson,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            @Header(value = KafkaHeaders.EXCEPTION_STACKTRACE, required = false) String stackTrace,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        try {
            log.error("Order event sent to DLQ - Partition: {}, Offset: {}", partition, offset);
            log.debug("Order JSON: {}", orderJson);

            FailedEvent failedEvent = FailedEvent.builder()
                    .originalTopic("order.paid")
                    .eventKey(key)
                    .eventPayload(orderJson)
                    .exceptionMessage(truncateMessage(exceptionMessage, 1000))
                    .stackTrace(truncateMessage(stackTrace, 5000))
                    .status(FailedEvent.FailedEventStatus.PENDING)
                    .retryCount(0)
                    .maxRetries(10)
                    .build();

            failedEvent.calculateNextRetryTime();
            failedEventRepository.save(failedEvent);

            log.info("Failed order event stored for automatic reprocessing. Will retry at: {}",
                    failedEvent.getNextRetryAt());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to store DLQ event in database", e);
            throw new RuntimeException("Failed to process DLQ event", e);
        }
    }

    @KafkaListener(topics = "product.sync.dlq", groupId = "ecommerce-dlq-group", containerFactory = "kafkaListenerContainerFactory")
    public void handleProductSyncDLQ(
            @Payload String productJson,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String exceptionMessage,
            @Header(value = KafkaHeaders.EXCEPTION_STACKTRACE, required = false) String stackTrace,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        try {
            log.error("Product sync event sent to DLQ - Partition: {}, Offset: {}", partition, offset);
            log.debug("Product JSON: {}", productJson);

            FailedEvent failedEvent = FailedEvent.builder()
                    .originalTopic("product.sync")
                    .eventKey(key)
                    .eventPayload(productJson)
                    .exceptionMessage(truncateMessage(exceptionMessage, 1000))
                    .stackTrace(truncateMessage(stackTrace, 5000))
                    .status(FailedEvent.FailedEventStatus.PENDING)
                    .retryCount(0)
                    .maxRetries(10)
                    .build();

            failedEvent.calculateNextRetryTime();
            failedEventRepository.save(failedEvent);

            log.info("Failed product sync event stored for automatic reprocessing. Will retry at: {}",
                    failedEvent.getNextRetryAt());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to store DLQ event in database", e);
            throw new RuntimeException("Failed to process DLQ event", e);
        }
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null) {
            return null;
        }
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength - 3) + "...";
    }
}