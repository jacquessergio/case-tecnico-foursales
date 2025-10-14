# Visão Geral do Projeto

Esta é uma **plataforma de e-commerce full-stack** construída com arquitetura de microsserviços orientada a eventos. O sistema demonstra padrões de nível empresarial, incluindo outbox transacional, circuit breakers, limitação de taxa e garantias de consistência eventual.

**Dois microsserviços:**
- `case-ecommerce-microservice` (Porta 8080): API Principal - Autenticação de usuários, catálogo de produtos, gerenciamento de pedidos
- `case-ecommerce-consumer` (Porta 8081): Consumidor Kafka - Atualizações assíncronas de estoque, sincronização de produtos, reprocessamento de DLQ

## Comandos de Build & Execução

### Configuração da Infraestrutura
```bash
# Iniciar todos os serviços de infraestrutura (MySQL, Elasticsearch, Kafka, Zookeeper, Kafka UI)
cd case-ecommerce-microservice
docker-compose up -d

# Verificar saúde da infraestrutura
docker-compose ps

# Parar infraestrutura
docker-compose down
```

### Microsserviço Principal (Porta 8080)
```bash
cd case-ecommerce-microservice

# Build
mvn clean package

# Executar
mvn spring-boot:run

# Executar testes
mvn test

# Executar teste único
mvn test -Dtest=DateParseUtilsTest
```

### Microsserviço Consumidor (Porta 8081)
```bash
cd case-ecommerce-consumer

# Build
mvn clean package

# Executar
mvn spring-boot:run
```

**Ordem de execução:** Infraestrutura → API Principal → Consumidor

## Padrões Críticos de Arquitetura

### 1. Padrão Transactional Outbox
**Problema:** Antipadrão de escrita dupla (escrita no banco de dados + envio ao Kafka não são atômicos)

**Solução:** Eventos salvos na tabela `outbox_events` dentro da mesma transação, publicados por job agendado

**Implementação:**
- Serviços chamam `OutboxService.saveEvent()` dentro de métodos `@Transactional`
- Job agendado `OutboxPublisher` (a cada 5s) publica eventos não publicados no Kafka
- Garantias: Entrega pelo menos uma vez, eventos sobrevivem a crashes da aplicação

**Crítico:** Todas as operações que produzem eventos DEVEM ser `@Transactional` para que o padrão outbox funcione

### 2. Dead Letter Queue (DLQ) + Backoff Exponencial
**Consumidor lida com falhas:**
- Kafka tenta 3x imediatamente (delay de 1s)
- Se ainda falhar → tópico DLQ (`order.paid.dlq`, `product.sync.dlq`)
- `DeadLetterQueueConsumer` persiste na tabela `failed_events`
- `FailedEventReprocessor` (agendado a cada 2 min) reprocessa com backoff exponencial: 1m → 2m → 4m → 8m → 16m → 32m → 60m (máx)

**Máquina de Estados:** PENDING → RETRYING → PROCESSED (ou MAX_RETRIES_REACHED após 10 tentativas)

### 3. Idempotência (Entrega Pelo Menos Uma Vez)
**Consumidor verifica tabela `processed_events` antes de processar:**
- Restrição única em `event_id` (UUID do payload do evento)
- Previne processamento duplicado quando Kafka reenvia mensagens
- **Crítico:** Todos os consumidores Kafka DEVEM verificar idempotência antes de processar

### 4. Proteção com Circuit Breaker
**Três circuit breakers configurados (Resilience4j):**
- **Elasticsearch** (agressivo: 50% falha → OPEN, espera 30s) - Tem fallback MySQL
- **Kafka** (moderado: 60% falha → OPEN, espera 60s) - Protege thread pool
- **MySQL** (conservador: 70% falha → OPEN) - Apenas exceções de infraestrutura ativam o breaker

**IMPORTANTE:** Exceções de negócio (`ResourceNotFoundException`, `BusinessException`) NÃO ativam o circuit breaker do MySQL

### 5. Padrão Dual-Repository (MySQL + Elasticsearch)
**Pacotes de repositório separados para evitar conflitos do Spring Data:**
- `repository.jpa.*` - MySQL com ACID (`@EnableJpaRepositories`)
- `repository.search.*` - Busca full-text Elasticsearch (`@EnableElasticsearchRepositories`)

**Fluxo de sincronização de produto:**
1. ProductService salva no MySQL + publica `ProductSyncEvent` via Outbox
2. OutboxPublisher → tópico Kafka `product.sync`
3. ProductSyncEventConsumer → Atualiza Elasticsearch
4. Verificação de idempotência previne duplicatas

**NUNCA misture anotações JPA e Elasticsearch na mesma entidade**

## Estratégia de Migração de Banco de Dados (Flyway)

**Configuração:**
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Apenas valida - NUNCA gera schema automaticamente
  flyway:
    enabled: true
    baseline-on-migrate: true
```

**Migrações (case-ecommerce-microservice):**
- `V1__initial_schema.sql` - Tabelas principais (users, products, orders, order_items, outbox_events)
- `V2__add_performance_indexes.sql` - 13 índices estratégicos
- `V3__add_idempotency_key.sql` - Idempotência de pedidos (previne pedidos duplicados)

**Migrações do consumidor:**
- Compartilha banco de dados com app principal
- `V6__create_failed_events_table.sql` - Persistência de DLQ
- Configuração usa `ignore-migration-patterns: "*:missing"` (V1-V5 do app principal)

**Para adicionar migração:**
1. Criar `src/main/resources/db/migration/V{N}__{descricao}.sql`
2. Escrever DDL (ALTER TABLE, CREATE INDEX, etc.)
3. Reiniciar app - Flyway aplica automaticamente
4. **NUNCA modifique migrações executadas** (validação de checksum)

## Arquitetura de Segurança

### Limitação de Taxa (Token Bucket - Bucket4j)
**Ordem da cadeia de filtros:** RateLimitFilter → JwtAuthenticationFilter → Controllers

**Limites por tipo de endpoint:**
- AUTH (login/registro): 5 req/min por IP
- PUBLIC (busca de produtos): 100 req/min por IP
- USER (pedidos): 30 req/min por ID de usuário
- ADMIN (CRUD de produtos): 60 req/min por ID de usuário
- SEARCH: 60 req/min
- REPORT: 10 req/min (agregações pesadas no BD)

**Cabeçalhos de resposta:**
- `X-RateLimit-Remaining`: Requisições restantes
- `X-RateLimit-Reset`: Timestamp de reset
- `Retry-After`: Segundos para esperar (quando bloqueado)

### Autenticação JWT
- Algoritmo: HS512 (segredo de 512 bits no `application.yml`)
- Expiração: 24 horas
- Claims: userId, email, role, iat, exp
- **Requisitos de senha:** 12+ caracteres, maiúsculas, minúsculas, números, caracteres especiais
- **Bloqueio de conta:** 5 tentativas falhas → bloqueio de 30 min (cache Caffeine)

### RBAC (Controle de Acesso Baseado em Papéis)
- Papéis: `ADMIN` (acesso completo), `USER` (apenas recursos próprios)
- Aplicação: `@PreAuthorize("hasRole('ADMIN')")` nos controllers
- Verificações de propriedade nos serviços (usuários só acessam seus próprios pedidos)

## Princípios de Organização de Código

### Inversão de Dependência (SOLID)
Controllers dependem de **interfaces** de serviço, não implementações:
```java
@RequiredArgsConstructor
public class OrderController {
    private final IOrderService orderService;  // Interface
}
```

**Interfaces:** `IOrderService`, `IProductService`, `IAuthService`, `IReportService`

### Bloqueio Pessimista (Prevenção de Condições de Corrida)
Operações de estoque usam locks de banco de dados para prevenir ataques TOCTOU:
```java
@Lock(PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdForUpdate(@Param("id") UUID id);
```

**Crítico:** Todas as operações que modificam estoque DEVEM usar `findByIdForUpdate()`

### Isolamento de Transação (Jobs Agendados)
Cada evento falho processado em **transação isolada** para prevenir cascatas de rollback:
```java
@Scheduled(fixedDelay = 120000)  // SEM @Transactional aqui
public void reprocessFailedEvents() {
    for (FailedEvent event : events) {
        processEventInTransaction(event);  // Cada um tem seu próprio @Transactional
    }
}
```

## Detalhes Críticos de Implementação

### Armazenamento de UUID (Compatibilidade MySQL)
**DEVE usar esta anotação para todas as chaves primárias UUID:**
```java
@Id
@GeneratedValue(generator = "UUID")
@GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
@JdbcTypeCode(SqlTypes.VARCHAR)  // ← CRÍTICO - armazena como string, não binário
@Column(columnDefinition = "CHAR(36)")
private UUID id;
```

**Sem `@JdbcTypeCode(SqlTypes.VARCHAR)`:** Hibernate 6.x armazena como binário → erro de charset do MySQL

### Estratégia de Armazenamento de Enum
Use VARCHAR, não tipo ENUM do MySQL (evita falhas no `ddl-auto: validate`):
```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
private OrderStatus status;
```

### Configuração Jackson JSR310
Projeto requer suporte a Java 8 time:
```java
@Bean
public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
}
```

## Monitoramento & Endpoints Admin

**API Principal (requer papel ADMIN):**
- `GET /api/v1/admin/circuit-breakers/status` - Estados dos circuit breakers
- `POST /api/v1/admin/circuit-breakers/{name}/transition?targetState=CLOSED` - Forçar mudança de estado
- `GET /api/v1/admin/outbox/stats` - Estatísticas de eventos outbox
- `GET /api/v1/admin/outbox/stuck` - Eventos travados em PENDING
- `POST /api/v1/admin/outbox/retry/{id}` - Retry manual
- `GET /api/v1/admin/rate-limit/stats` - Estatísticas de limitação de taxa

**Swagger UI:** http://localhost:8080/swagger-ui.html  
**Kafka UI:** http://localhost:8090

## Testes & Documentação

**Exemplos de teste:** Veja `case-ecommerce-microservice/docs/TESTING_EXAMPLES.md` para exemplos curl

**Documentos importantes:**
- `case-ecommerce-microservice/README.md` - Guia abrangente da API Principal
- `case-ecommerce-consumer/README.md` - Detalhes de implementação do Consumidor
- `case-ecommerce-microservice/docs/RATE_LIMITING_GUIDE.md` - Aprofundamento em limitação de taxa
- `case-ecommerce-microservice/docs/SECURITY_AUDIT_REPORT.md` - Análise de segurança
- `case-ecommerce-microservice/docs/QA_TEST_REPORT.md` - Relatório de testes QA

## Problemas Comuns & Soluções

### Circuit Breaker Sempre OPEN
**Diagnóstico:** Verifique saúde das dependências
```bash
# Elasticsearch
curl http://localhost:9200/_cluster/health

# Forçar fechamento do circuit
curl -X POST http://localhost:8080/api/v1/admin/circuit-breakers/elasticsearch/transition?targetState=CLOSED \
  -H "Authorization: Bearer {admin-token}"
```

### Eventos Não Sendo Reprocessados
**Diagnóstico:** Verifique se jobs agendados estão rodando
```bash
grep "reprocessFailedEvents" logs/application.log
```

**Corrigir eventos travados em RETRYING:**
```sql
UPDATE failed_events
SET status = 'PENDING', next_retry_at = NOW()
WHERE status = 'RETRYING' AND last_retry_at < NOW() - INTERVAL 30 MINUTE;
```

### Problemas de Sincronização Elasticsearch
**Correção:** Deletar índice e reiniciar (irá reconstruir a partir dos eventos MySQL)
```bash
curl -X DELETE "localhost:9200/products"
# Reiniciar API principal
```

### Lag do Consumidor Kafka
```bash
# Verificar lag
docker exec -it ecommerce-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group ecommerce-stock-group \
  --describe
```

## Versões da Stack

- Java: 17 LTS
- Spring Boot: 3.2.0
- MySQL: 8.0
- Elasticsearch: 8.11.0
- Apache Kafka: 2.8+ (Confluent Platform 7.4.0)
- Resilience4j: 2.1.0
- Bucket4j: 8.7.0
- JJWT: 0.11.5
- Flyway: 9.x
