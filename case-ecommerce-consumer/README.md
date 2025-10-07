# E-Commerce Event Consumer

**Microsserviço Kafka** implementando processamento resiliente de eventos com **Dead Letter Queue (DLQ) automático**, **exponential backoff** e **garantias de consistência eventual**.

---

## 🏗️ Arquitetura

### Responsabilidades

1. **Stock Update** - Redução de estoque após pagamento confirmado
2. **Product Sync** - Sincronização MySQL ↔ Elasticsearch
3. **DLQ Reprocessing** - Retry automático de eventos falhos com backoff exponencial

### Fluxo de Processamento

```
┌────────────────────────────────────────────────────────────┐
│  Kafka Topics (from Main API)                              │
│  • order.paid - Payment confirmation events                │
│  • product.sync - Product catalog synchronization          │
└────────────────────────────────────────────────────────────┘
                           │
                           ↓
┌────────────────────────────────────────────────────────────┐
│  Event Consumers (Manual Acknowledgment)                   │
│  ┌──────────────────┐  ┌──────────────────┐               │
│  │ OrderEvent       │  │ ProductSyncEvent │               │
│  │ Consumer         │  │ Consumer         │               │
│  └──────────────────┘  └──────────────────┘               │
│         ↓ Success            ↓ Success                     │
│  ┌──────────────────┐  ┌──────────────────┐               │
│  │ StockUpdate      │  │ ProductSync      │               │
│  │ Service          │  │ Service          │               │
│  └──────────────────┘  └──────────────────┘               │
│         │                    │                             │
│         ↓                    ↓                             │
│  ┌─────────┐  ┌──────────┐  ┌──────────────┐             │
│  │ MySQL   │  │ Elastic  │  │ processed_   │             │
│  │ (Stock) │  │ (Docs)   │  │ events       │             │
│  └─────────┘  └──────────┘  └──────────────┘             │
└────────────────────────────────────────────────────────────┘
          ↓ Failure after 3 retries
┌────────────────────────────────────────────────────────────┐
│  Dead Letter Queue (DLQ)                                   │
│  order.paid.dlq, product.sync.dlq                          │
└────────────────────────────────────────────────────────────┘
          ↓
┌────────────────────────────────────────────────────────────┐
│  DeadLetterQueueConsumer                                   │
│  Persists failed events in failed_events table             │
└────────────────────────────────────────────────────────────┘
          ↓
┌────────────────────────────────────────────────────────────┐
│  FailedEventReprocessor (@Scheduled every 2 min)           │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Exponential Backoff Retry:                           │  │
│  │ 1m → 2m → 4m → 8m → 16m → 32m → 60m (max)          │  │
│  │ Circuit Breaker Protection (Resilience4j)            │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
```

### Stack Tecnológico

| Camada | Tecnologia | Versão | Propósito |
|--------|------------|--------|-----------|
| **Runtime** | Java | 17 | LTS |
| **Framework** | Spring Boot | 3.2.0 | Microservices |
| **Messaging** | Apache Kafka | 2.8+ | Event streaming |
| **Persistence** | MySQL | 8.0 | Event storage, stock updates |
| **Schema Versioning** | Flyway | 9.x | Database migrations |
| **Search** | Elasticsearch | 8.11.0 | Product catalog sync |
| **Resilience** | Resilience4j | 2.1.0 | Circuit breaker |
| **Connection Pool** | HikariCP | Latest | Optimized DB connections |

---

## 🚀 Quick Start

### Prerequisites
- Java 17+
- Maven 3.6+
- Infrastructure running (MySQL, Kafka, Elasticsearch)
- Main microservice running (event producer)

### Build & Run
```bash
cd case-ecommerce-consumer

# Build
mvn clean package

# Run (porta 8081)
mvn spring-boot:run
```

**Health Check:**
```bash
curl http://localhost:8081/actuator/health
```

---

## 🔑 Funcionalidades Críticas

### 1. Idempotência (Previne Duplicatas)

**Problema:** Kafka garante **at-least-once delivery** (pode entregar mensagem 2x)

**Solução:** Tabela `processed_events` com UNIQUE constraint no `event_id`

```java
// ProductSyncService
if (processedEventRepository.existsByEventId(event.getEventId())) {
    log.warn("⚠️ Event {} already processed. Skipping.", event.getEventId());
    return;
}

// Process event...

// Mark as processed
ProcessedEvent processedEvent = ProcessedEvent.builder()
    .eventId(event.getEventId())  // UUID from event
    .eventType(event.getEventType().name())
    .aggregateId(event.getProductId().toString())
    .status(ProcessedEventStatus.SUCCESS)
    .build();
processedEventRepository.save(processedEvent);
```

**Schema:**
```sql
CREATE TABLE processed_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,  -- Idempotency key
    event_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(500),
    INDEX idx_event_id (event_id)
);
```

---

### 2. Dead Letter Queue (DLQ) + Automatic Reprocessing

**Problema:** Quando Elasticsearch está offline, eventos de sync falham e são perdidos

**Solução:** Persistência em `failed_events` table + retry automático com exponential backoff

#### Arquitetura DLQ

**Step 1: Kafka Retry (3 tentativas imediatas)**
```java
// KafkaConsumerConfig
DefaultErrorHandler errorHandler = new DefaultErrorHandler(
    new DeadLetterPublishingRecoverer(kafkaTemplate,
        (record, ex) -> new TopicPartition(record.topic() + ".dlq", -1)
    ),
    new FixedBackOff(1000L, 3L)  // 3 retries, 1s delay
);
```

**Step 2: DLQ Consumer (Persistência)**
```java
@KafkaListener(topics = "order.paid.dlq", groupId = "ecommerce-dlq-group")
@Transactional
public void handleOrderPaidDLQ(
    @Payload String orderJson,
    @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
    Acknowledgment acknowledgment
) {
    FailedEvent failedEvent = FailedEvent.builder()
            .originalTopic("order.paid")
            .eventPayload(orderJson)
            .exceptionMessage(exceptionMessage)
            .status(FailedEventStatus.PENDING)
            .retryCount(0)
            .maxRetries(10)
            .build();

    failedEvent.calculateNextRetryTime();  // Exponential backoff
    failedEventRepository.save(failedEvent);
    acknowledgment.acknowledge();

    log.info("✅ Failed event stored. Retry at: {}", failedEvent.getNextRetryAt());
}
```

**Step 3: Exponential Backoff Calculation**
```java
// FailedEvent.java
public void calculateNextRetryTime() {
    long baseDelayMinutes = 1;
    long maxDelayMinutes = 60;

    // 2^retryCount * baseDelay, capped at maxDelay
    long delayMinutes = Math.min(
        baseDelayMinutes * (long) Math.pow(2, retryCount),
        maxDelayMinutes
    );

    nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
}

// Results:
// Retry 0: now + 1 min
// Retry 1: now + 2 min
// Retry 2: now + 4 min
// Retry 3: now + 8 min
// Retry 4: now + 16 min
// Retry 5: now + 32 min
// Retry 6-10: now + 60 min (max)
```

**Step 4: Automatic Reprocessing**
```java
// FailedEventReprocessor.java
@Scheduled(fixedDelay = 120000, initialDelay = 60000)  // Every 2 min
public void reprocessFailedEvents() {
    LocalDateTime now = LocalDateTime.now();

    // Find events ready for retry (respecting backoff)
    List<FailedEvent> eventsToRetry = failedEventRepository
        .findEventsReadyForRetry(now, PageRequest.of(0, BATCH_SIZE));

    for (FailedEvent event : eventsToRetry) {
        processEventInTransaction(event, now);  // Isolated TX
    }
}

@Transactional  // ⬅️ Each event has own transaction
protected void processEventInTransaction(FailedEvent event, LocalDateTime now) {
    event.setStatus(FailedEventStatus.RETRYING);
    event.setLastRetryAt(now);
    failedEventRepository.save(event);

    // Process with Circuit Breaker protection
    boolean success = reprocessEvent(event);

    if (success) {
        event.markAsProcessed("Successfully reprocessed");
        log.info("✅ Reprocessed event {} after {} retries",
                event.getId(), event.getRetryCount());
    } else {
        handleRetryFailure(event);
    }

    failedEventRepository.save(event);
}
```

**Step 5: Circuit Breaker Protection**
```java
@CircuitBreaker(name = "eventReprocessor", fallbackMethod = "reprocessEventFallback")
private boolean reprocessEvent(FailedEvent event) throws Exception {
    switch (event.getOriginalTopic()) {
        case "order.paid", "order.paid.dlq" -> {
            JsonNode orderNode = objectMapper.readTree(event.getEventPayload());
            UUID orderUuid = UUID.fromString(orderNode.get("id").asText());
            stockUpdateService.updateProductStock(orderUuid);
            return true;
        }
        case "product.sync", "product.sync.dlq" -> {
            ProductSyncEvent productEvent = objectMapper.readValue(
                event.getEventPayload(), ProductSyncEvent.class
            );
            productSyncService.processProductSyncEvent(productEvent);
            return true;
        }
    }
}

private boolean reprocessEventFallback(FailedEvent event, Exception ex) {
    log.warn("⚡ Circuit breaker OPEN. Event {} will be retried later.", event.getId());
    return false;
}
```

#### Failed Event Status State Machine

```
PENDING ──────────────────────────┐
   │                               │
   │ nextRetryAt <= now            │
   ↓                               │
RETRYING ──── success ──→ PROCESSED│
   │                               │
   │ failure & retryCount < 10     │
   └───────────────────────────────┘
   │ failure & retryCount >= 10
   ↓
MAX_RETRIES_REACHED (manual intervention)
```

#### Garantias

| Garantia | Descrição |
|----------|-----------|
| **Persistence** | Eventos sobrevivem a application restarts |
| **Eventual Consistency** | Processados quando Elasticsearch recupera |
| **Backpressure** | Exponential backoff previne overload |
| **Circuit Breaker** | Protege contra cascading failures |
| **Batch Processing** | Max 10 eventos por run (resource control) |
| **Monitoring** | Full error context tracking |

---

## 🗄️ Database Schema

### failed_events (DLQ Persistence)

```sql
CREATE TABLE IF NOT EXISTS failed_events (
    id CHAR(36) PRIMARY KEY,                     -- UUID
    original_topic VARCHAR(100) NOT NULL,        -- order.paid, product.sync
    event_payload TEXT NOT NULL,                 -- Full JSON event
    exception_message TEXT,                      -- Error from consumer
    stack_trace TEXT,                            -- Full stack trace
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- State machine
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 10,
    next_retry_at TIMESTAMP NULL,                -- Exponential backoff
    last_retry_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    processing_notes TEXT,
    INDEX idx_failed_events_status (status),
    INDEX idx_failed_events_next_retry (next_retry_at),
    INDEX idx_failed_events_created (created_at),
    INDEX idx_failed_events_topic (original_topic)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**Status Values:**
- `PENDING` - Ready for retry
- `RETRYING` - Currently being processed
- `PROCESSED` - Successfully reprocessed
- `FAILED` - Temporary failure
- `MAX_RETRIES_REACHED` - Manual intervention required

### processed_events (Idempotency)

```sql
CREATE TABLE IF NOT EXISTS processed_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,       -- Idempotency key
    event_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(500),
    INDEX idx_processed_event_id (event_id),
    INDEX idx_processed_aggregate_id (aggregate_id)
);
```

---

## 🔧 Configuração

### Kafka Consumer
```yaml
kafka:
  consumer:
    enable-auto-commit: false  # Manual ACK for reliability
    group-id: ecommerce-stock-group
```

### Circuit Breaker
```yaml
resilience4j:
  circuitbreaker:
    instances:
      eventReprocessor:
        failure-rate-threshold: 50%
        wait-duration-in-open-state: 60s
        sliding-window-size: 10
```

### Scheduled Jobs

| Job | Frequency | Purpose |
|-----|-----------|---------|
| `reprocessFailedEvents()` | Every 2 min | Process failed events with backoff |
| `cleanupOldProcessedEvents()` | Daily 3 AM | Delete processed events > 30 days |
| `monitorStuckEvents()` | Every hour | Reset stuck RETRYING events |

---

## 📦 Flyway Migrations

### Strategy
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Only validates, does NOT modify schema
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    validate-on-migrate: false  # Consumer shares DB with main app
    ignore-migration-patterns: "*:missing"  # V1-V5 from main app
```

### Migrations

**V1__create_processed_events_table.sql** - Idempotency support
**V6__create_failed_events_table.sql** - DLQ automatic reprocessing

**IMPORTANT:** V1-V5 são aplicadas pelo microserviço principal (shared database)

---

## 🛠️ Monitoramento

### Failed Events Statistics
```bash
# Connect to MySQL
docker exec -it ecommerce-mysql mysql -u ecommerce_user -pecommerce_password ecommerce_db

# Get statistics
SELECT
    status,
    COUNT(*) as count,
    MIN(created_at) as oldest,
    MAX(created_at) as newest
FROM failed_events
GROUP BY status;

# Find events needing manual intervention
SELECT id, original_topic, retry_count, exception_message
FROM failed_events
WHERE status = 'MAX_RETRIES_REACHED'
ORDER BY created_at DESC;

# Find next retries
SELECT id, original_topic, retry_count, next_retry_at
FROM failed_events
WHERE status = 'PENDING'
ORDER BY next_retry_at ASC
LIMIT 10;
```

### Kafka Monitoring
```bash
# Kafka UI
open http://localhost:8090

# List DLQ messages
docker exec -it ecommerce-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order.paid.dlq \
  --from-beginning

# Check consumer lag
docker exec -it ecommerce-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group ecommerce-stock-group \
  --describe
```

---

## 🐛 Troubleshooting

### Events Not Being Reprocessed
**Diagnosis:**
```bash
# Check if scheduled jobs are running
grep "reprocessFailedEvents" logs/application.log

# Verify @EnableScheduling annotation
grep -r "@EnableScheduling" src/main/java
```

### Circuit Breaker Always OPEN
**Solution:**
```bash
# Check Elasticsearch health
curl http://localhost:9200/_cluster/health

# Adjust thresholds in application.yml
resilience4j:
  circuitbreaker:
    instances:
      eventReprocessor:
        failure-rate-threshold: 70%  # More tolerant
```

### Stuck RETRYING Events
**Solution:**
```sql
-- Manual reset
UPDATE failed_events
SET status = 'PENDING', next_retry_at = NOW()
WHERE status = 'RETRYING'
  AND last_retry_at < NOW() - INTERVAL 30 MINUTE;
```

---

## 🎯 Detalhes Técnicos Críticos

### 1. UUID Storage (MySQL)
**CRITICAL:** Must use `@JdbcTypeCode(SqlTypes.VARCHAR)` to store as string (not binary)

```java
@Id
@GeneratedValue(generator = "UUID")
@GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
@JdbcTypeCode(SqlTypes.VARCHAR)  // ← REQUIRED
@Column(columnDefinition = "CHAR(36)")
private UUID id;
```

**Without:** Hibernate 6.x stores UUIDs as binary → MySQL error:
```
Incorrect string value: '\x83\xE6iH...' for column 'id'
```

### 2. Transaction Isolation (Scheduled Jobs)
Each event processed in **isolated transaction** to prevent rollback cascades:

```java
@Scheduled(fixedDelay = 120000)  // ← NO @Transactional here
public void reprocessFailedEvents() {
    for (FailedEvent event : events) {
        processEventInTransaction(event, now);  // ← Each has own TX
    }
}

@Transactional  // ← Transaction scope per event
protected void processEventInTransaction(FailedEvent event, LocalDateTime now) {
    // Process - exceptions won't affect other events
}
```

**Why:** Without isolation, one failed event marks entire batch for rollback:
```
Transaction silently rolled back because it has been marked as rollback-only
```

### 3. Flyway Configuration (Shared Database)
Consumer shares database with main app:

```yaml
spring:
  flyway:
    enabled: true
    validate-on-migrate: false  # Don't validate missing migrations
    ignore-migration-patterns: "*:missing"  # V1-V5 from main app
```

**Migrations:**
- V1-V5: Applied by main app
- V6: First consumer-specific migration (`failed_events` table)

### 4. Enum Storage Strategy
Status uses VARCHAR, not MySQL ENUM:

```java
@Enumerated(EnumType.STRING)  // ← Store as string
@Column(nullable = false, length = 20)
private FailedEventStatus status;
```

```sql
status VARCHAR(20) NOT NULL  -- ← Not ENUM type
```

**Why:** Hibernate `ddl-auto: validate` fails when expecting MySQL ENUM.

---

## ✅ Checklist de Produção

### Monitoring & Alerting
- [ ] Alerting for `MAX_RETRIES_REACHED` events
- [ ] Dashboard for `failed_events` table statistics
- [ ] Monitor consumer lag in Kafka
- [ ] Alert on Circuit Breaker state transitions
- [ ] Track average retry count per event type

### Performance Tuning
- [ ] Adjust `BATCH_SIZE` based on load (default: 10)
- [ ] Review HikariCP pool sizes (min: 5, max: 10)
- [ ] Tune Kafka consumer concurrency (default: 3 threads)
- [ ] Configure retry delays for SLA requirements
- [ ] Set Circuit Breaker thresholds based on SLO

### Database Maintenance
- [ ] Schedule cleanup job for old `processed_events` (>30 days)
- [ ] Set up index maintenance for `failed_events` table
- [ ] Configure database backups
- [ ] Monitor table sizes and implement archiving

### Security & Reliability
- [ ] Externalize database credentials (environment variables)
- [ ] Configure TLS for Kafka connections (production)
- [ ] Set appropriate log levels (INFO in prod, DEBUG in dev)
- [ ] Implement distributed tracing (Zipkin/Jaeger)
- [ ] Configure health checks and liveness probes

---

## 📚 Documentação Relacionada

- [CLAUDE.md](../CLAUDE.md) - Guia completo do projeto
- [case-ecommerce-microservice/README.md](../case-ecommerce-microservice/README.md) - Main API
- [SECURITY_IMPROVEMENTS_APPLIED.md](../SECURITY_IMPROVEMENTS_APPLIED.md) - Security enhancements

---

**Versão**: 1.0.1
**Última Atualização**: Janeiro 2025
**Tech Stack**: Spring Boot 3.2.0 + Kafka + MySQL + Elasticsearch + Resilience4j

### Destaques Técnicos

✅ **Eventual Consistency** - DLQ automático com exponential backoff
✅ **Guaranteed Delivery** - At-least-once com idempotência
✅ **Circuit Breaker** - Proteção contra cascading failures
✅ **Isolated Transactions** - Prevents rollback cascades
✅ **UUID as VARCHAR** - MySQL charset compatibility
✅ **Shared Database** - Proper Flyway configuration
