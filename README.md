## Project Overview

This is a **full-stack e-commerce platform** built with event-driven microservices architecture. The system demonstrates enterprise-grade patterns including transactional outbox, circuit breakers, rate limiting, and eventual consistency guarantees.

**Two microservices:**
- `case-ecommerce-microservice` (Port 8080): Main API - User auth, product catalog, order management
- `case-ecommerce-consumer` (Port 8081): Kafka consumer - Async stock updates, product sync, DLQ reprocessing

## Build & Run Commands

### Infrastructure Setup
```bash
# Start all infrastructure services (MySQL, Elasticsearch, Kafka, Zookeeper, Kafka UI)
cd case-ecommerce-microservice
docker-compose up -d

# Verify infrastructure health
docker-compose ps

# Stop infrastructure
docker-compose down
```

### Main Microservice (Port 8080)
```bash
cd case-ecommerce-microservice

# Build
mvn clean package

# Run
mvn spring-boot:run

# Run tests
mvn test

# Run single test
mvn test -Dtest=DateParseUtilsTest
```

### Consumer Microservice (Port 8081)
```bash
cd case-ecommerce-consumer

# Build
mvn clean package

# Run
mvn spring-boot:run
```

**Run order:** Infrastructure → Main API → Consumer

## Critical Architecture Patterns

### 1. Transactional Outbox Pattern
**Problem:** Dual-write antipattern (database write + Kafka send not atomic)

**Solution:** Events saved in `outbox_events` table within same transaction, published by scheduled job

**Implementation:**
- Services call `OutboxService.saveEvent()` within `@Transactional` methods
- `OutboxPublisher` scheduled job (every 5s) publishes unpublished events to Kafka
- Guarantees: At-least-once delivery, events survive application crashes

**Critical:** All event-producing operations MUST be `@Transactional` for outbox pattern to work

### 2. Dead Letter Queue (DLQ) + Exponential Backoff
**Consumer handles failures:**
- Kafka retries 3x immediately (1s delay)
- If still failing → DLQ topic (`order.paid.dlq`, `product.sync.dlq`)
- `DeadLetterQueueConsumer` persists to `failed_events` table
- `FailedEventReprocessor` (scheduled every 2 min) retries with exponential backoff: 1m → 2m → 4m → 8m → 16m → 32m → 60m (max)

**State Machine:** PENDING → RETRYING → PROCESSED (or MAX_RETRIES_REACHED after 10 retries)

### 3. Idempotency (At-Least-Once Delivery)
**Consumer checks `processed_events` table before processing:**
- Unique constraint on `event_id` (UUID from event payload)
- Prevents duplicate processing when Kafka redelivers messages
- **Critical:** All Kafka consumers MUST check idempotency before processing

### 4. Circuit Breaker Protection
**Three circuit breakers configured (Resilience4j):**
- **Elasticsearch** (aggressive: 50% failure → OPEN, 30s wait) - Has MySQL fallback
- **Kafka** (moderate: 60% failure → OPEN, 60s wait) - Protects thread pool
- **MySQL** (conservative: 70% failure → OPEN) - Only infrastructure exceptions trigger breaker

**IMPORTANT:** Business exceptions (`ResourceNotFoundException`, `BusinessException`) DO NOT trigger MySQL circuit breaker

### 5. Dual-Repository Pattern (MySQL + Elasticsearch)
**Separate repository packages to avoid Spring Data conflicts:**
- `repository.jpa.*` - MySQL with ACID (`@EnableJpaRepositories`)
- `repository.search.*` - Elasticsearch full-text search (`@EnableElasticsearchRepositories`)

**Product sync flow:**
1. ProductService saves to MySQL + publishes `ProductSyncEvent` via Outbox
2. OutboxPublisher → Kafka topic `product.sync`
3. ProductSyncEventConsumer → Updates Elasticsearch
4. Idempotency check prevents duplicates

**NEVER mix JPA and Elasticsearch annotations on same entity**

## Database Migration Strategy (Flyway)

**Configuration:**
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Only validates - NEVER auto-generates schema
  flyway:
    enabled: true
    baseline-on-migrate: true
```

**Migrations (case-ecommerce-microservice):**
- `V1__initial_schema.sql` - Core tables (users, products, orders, order_items, outbox_events)
- `V2__add_performance_indexes.sql` - 13 strategic indexes
- `V3__add_idempotency_key.sql` - Order idempotency (prevents duplicate orders)

**Consumer migrations:**
- Shares database with main app
- `V6__create_failed_events_table.sql` - DLQ persistence
- Config uses `ignore-migration-patterns: "*:missing"` (V1-V5 from main app)

**To add migration:**
1. Create `src/main/resources/db/migration/V{N}__{description}.sql`
2. Write DDL (ALTER TABLE, CREATE INDEX, etc.)
3. Restart app - Flyway applies automatically
4. **NEVER modify executed migrations** (checksum validation)

## Security Architecture

### Rate Limiting (Token Bucket - Bucket4j)
**Filter chain order:** RateLimitFilter → JwtAuthenticationFilter → Controllers

**Limits per endpoint type:**
- AUTH (login/register): 5 req/min per IP
- PUBLIC (product search): 100 req/min per IP
- USER (orders): 30 req/min per user ID
- ADMIN (product CRUD): 60 req/min per user ID
- SEARCH: 60 req/min
- REPORT: 10 req/min (heavy DB aggregations)

**Response headers:**
- `X-RateLimit-Remaining`: Requests left
- `X-RateLimit-Reset`: Reset timestamp
- `Retry-After`: Seconds to wait (when blocked)

### JWT Authentication
- Algorithm: HS512 (512-bit secret in `application.yml`)
- Expiration: 24 hours
- Claims: userId, email, role, iat, exp
- **Password requirements:** 12+ chars, uppercase, lowercase, numbers, special chars
- **Account lockout:** 5 failed attempts → 30 min block (Caffeine cache)

### RBAC (Role-Based Access Control)
- Roles: `ADMIN` (full access), `USER` (own resources only)
- Enforcement: `@PreAuthorize("hasRole('ADMIN')")` on controllers
- Ownership checks in services (users can only access their own orders)

## Code Organization Principles

### Dependency Inversion (SOLID)
Controllers depend on service **interfaces**, not implementations:
```java
@RequiredArgsConstructor
public class OrderController {
    private final IOrderService orderService;  // Interface
}
```

**Interfaces:** `IOrderService`, `IProductService`, `IAuthService`, `IReportService`

### Pessimistic Locking (Race Condition Prevention)
Stock operations use database locks to prevent TOCTOU attacks:
```java
@Lock(PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdForUpdate(@Param("id") UUID id);
```

**Critical:** All stock-modifying operations MUST use `findByIdForUpdate()`

### Transaction Isolation (Scheduled Jobs)
Each failed event processed in **isolated transaction** to prevent rollback cascades:
```java
@Scheduled(fixedDelay = 120000)  // NO @Transactional here
public void reprocessFailedEvents() {
    for (FailedEvent event : events) {
        processEventInTransaction(event);  // Each has own @Transactional
    }
}
```

## Critical Implementation Details

### UUID Storage (MySQL Compatibility)
**MUST use this annotation for all UUID primary keys:**
```java
@Id
@GeneratedValue(generator = "UUID")
@GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
@JdbcTypeCode(SqlTypes.VARCHAR)  // ← CRITICAL - stores as string, not binary
@Column(columnDefinition = "CHAR(36)")
private UUID id;
```

**Without `@JdbcTypeCode(SqlTypes.VARCHAR)`:** Hibernate 6.x stores as binary → MySQL charset error

### Enum Storage Strategy
Use VARCHAR, not MySQL ENUM type (avoids `ddl-auto: validate` failures):
```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
private OrderStatus status;
```

### Jackson JSR310 Configuration
Project requires Java 8 time support:
```java
@Bean
public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
}
```

## Monitoring & Admin Endpoints

**Main API (requires ADMIN role):**
- `GET /api/v1/admin/circuit-breakers/status` - Circuit breaker states
- `POST /api/v1/admin/circuit-breakers/{name}/transition?targetState=CLOSED` - Force state change
- `GET /api/v1/admin/outbox/stats` - Outbox event statistics
- `GET /api/v1/admin/outbox/stuck` - Events stuck in PENDING
- `POST /api/v1/admin/outbox/retry/{id}` - Manual retry
- `GET /api/v1/admin/rate-limit/stats` - Rate limit statistics

**Swagger UI:** http://localhost:8080/swagger-ui.html
**Kafka UI:** http://localhost:8090

## Testing & Documentation

**Testing examples:** See `case-ecommerce-microservice/docs/TESTING_EXAMPLES.md` for curl examples

**Important docs:**
- `case-ecommerce-microservice/README.md` - Main API comprehensive guide
- `case-ecommerce-consumer/README.md` - Consumer implementation details
- `case-ecommerce-microservice/docs/RATE_LIMITING_GUIDE.md` - Rate limiting deep dive
- `case-ecommerce-microservice/docs/SECURITY_AUDIT_REPORT.md` - Security analysis
- `case-ecommerce-microservice/docs/QA_TEST_REPORT.md` - QA test report

## Common Issues & Solutions

### Circuit Breaker Always OPEN
**Diagnosis:** Check dependency health
```bash
# Elasticsearch
curl http://localhost:9200/_cluster/health

# Force close circuit
curl -X POST http://localhost:8080/api/v1/admin/circuit-breakers/elasticsearch/transition?targetState=CLOSED \
  -H "Authorization: Bearer {admin-token}"
```

### Events Not Being Reprocessed
**Diagnosis:** Check scheduled jobs running
```bash
grep "reprocessFailedEvents" logs/application.log
```

**Fix stuck RETRYING events:**
```sql
UPDATE failed_events
SET status = 'PENDING', next_retry_at = NOW()
WHERE status = 'RETRYING' AND last_retry_at < NOW() - INTERVAL 30 MINUTE;
```

### Elasticsearch Sync Issues
**Fix:** Delete index and restart (will rebuild from MySQL events)
```bash
curl -X DELETE "localhost:9200/products"
# Restart main API
```

### Kafka Consumer Lag
```bash
# Check lag
docker exec -it ecommerce-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group ecommerce-stock-group \
  --describe
```

## Stack Versions

- Java: 17 LTS
- Spring Boot: 3.2.0
- MySQL: 8.0
- Elasticsearch: 8.11.0
- Apache Kafka: 2.8+ (Confluent Platform 7.4.0)
- Resilience4j: 2.1.0
- Bucket4j: 8.7.0
- JJWT: 0.11.5
- Flyway: 9.x
