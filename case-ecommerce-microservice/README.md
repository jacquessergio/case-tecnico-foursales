
---

## 📋 Descrição do Projeto

Este projeto é uma **plataforma de e-commerce** desenvolvida com arquitetura de microsserviços, projetada para demonstrar a implementação de padrões enterprise-grade em sistemas distribuídos. O sistema oferece funcionalidades essenciais de comércio eletrônico, incluindo gerenciamento de produtos, processamento de pedidos, controle de estoque e autenticação de usuários, com foco especial em **confiabilidade**, **escalabilidade** e **segurança**.

### Principais Características

- **Arquitetura Event-Driven**: Comunicação assíncrona via Apache Kafka garantindo desacoplamento entre serviços
- **Consistência Eventual**: Implementação do padrão Transactional Outbox para garantia de entrega de eventos
- **Alta Disponibilidade**: Circuit Breakers e fallback strategies para resiliência a falhas de dependências
- **Segurança Robusta**: Autenticação JWT, RBAC, rate limiting, account lockout e validação de complexidade de senha
- **Performance Otimizada**: Pessimistic locking para operações críticas, índices estratégicos e pool de conexões HikariCP
- **Busca Avançada**: Integração com Elasticsearch para full-text search com fuzzy matching
- **Observabilidade**: Logs estruturados, métricas de circuit breakers, endpoints de administração e Swagger UI


## 📝 Requisitos do Sistema

### Requisitos Funcionais

| ID | Requisito | Descrição | Prioridade |
|----|-----------|-----------|------------|
| **RF-001** | Cadastro de Usuários | Sistema deve permitir cadastro de usuários com validação de email único e senha forte (8+ chars, maiúsculas, minúsculas, números, caracteres especiais) | Alta |
| **RF-002** | Autenticação JWT | Sistema deve autenticar usuários via email/senha e retornar token JWT com validade de 24h | Alta |
| **RF-003** | Gerenciamento de Produtos (Admin) | Administradores devem criar, atualizar, visualizar e remover produtos | Alta |
| **RF-004** | Usuario final (User) | Usuarios devem  criar pedidos, pagar pedidos e visualizar produtos | Alta |
| **RF-005** | Busca de Produtos | Usuários devem buscar produtos por nome com suporte a fuzzy matching e filtros por categoria | Alta |
| **RF-006** | Criação de Pedidos | Usuários autenticados devem criar pedidos com um ou mais produtos, validando estoque disponível em tempo real | Alta |
| **RF-007** | Confirmação de Pagamento | Sistema deve permitir confirmação de pagamento de pedidos com status PENDENTE, alterando para PAGO | Alta |
| **RF-008** | Redução de Estoque Assíncrona | Sistema deve reduzir estoque APENAS após confirmação de pagamento via eventos Kafka | Alta |
| **RF-009** | Sincronização MySQL ↔ Elasticsearch | Alterações em produtos no MySQL devem ser sincronizadas automaticamente com Elasticsearch via eventos | Alta |
| **RF-010** | Consulta de Pedidos | Usuários devem consultar apenas seus próprios pedidos; administradores podem consultar todos | Alta |
| **RF-011** | Prevenção de Pedidos Duplicados | Sistema deve implementar idempotency keys para prevenir criação de pedidos duplicados (double-click, retry) | Média |
| **RF-012** | Relatórios Administrativos | Administradores devem visualizar: top 5 usuários que mais compraram, ticket médio dos pedidos por usuário e valor total faturado no mês atual | Média |


### Requisitos Não Funcionais

| ID | Categoria | Requisito | Especificação | Implementação |
|----|-----------|-----------|---------------|---------------|
| **RNF-001** | **Disponibilidade** | Alta disponibilidade de leitura | Sistema deve manter disponibilidade de busca mesmo com Elasticsearch offline | Circuit Breaker + Fallback MySQL |
| **RNF-002** | **Disponibilidade** | Tolerância a falhas de mensageria | Sistema deve garantir entrega de eventos críticos mesmo com Kafka temporariamente indisponível | Transactional Outbox Pattern |
| **RNF-003** | **Consistência** | Consistência eventual | Sistema deve garantir consistência eventual entre MySQL e Elasticsearch em até 10 segundos | Event Sourcing + Idempotency |
| **RNF-004** | **Consistência** | Atomicidade de pagamentos | Alteração de status de pedido e publicação de evento devem ser atômicas | Transactional Outbox (ACID) |
| **RNF-005** | **Performance** | Tempo de resposta de APIs | 95% das requisições devem responder em < 500ms (P95) | Pessimistic Locking, HikariCP tuning, Database indexes |
| **RNF-006** | **Performance** | Busca de produtos | Busca por nome deve retornar resultados em < 200ms para catálogos de até 100k produtos | Elasticsearch com fuzzy matching |
| **RNF-007** | **Performance** | Throughput de pedidos | Sistema deve processar até 100 pedidos/segundo | Connection pooling, async processing |
| **RNF-008** | **Segurança** | Proteção contra brute force | Login deve bloquear conta após 5 tentativas falhas por 30 minutos | Account Lockout (Caffeine cache) |
| **RNF-009** | **Segurança** | Proteção contra DDoS | Endpoints públicos devem limitar requisições a 100/min por IP | Rate Limiting (Token Bucket) |
| **RNF-010** | **Segurança** | Autenticação stateless | Sistema deve usar autenticação stateless com tokens JWT (HS512) | JWT com 512-bit secret, expiration 24h |
| **RNF-011** | **Segurança** | Controle de acesso baseado em roles | Sistema deve implementar RBAC com roles USER e ADMIN | Spring Security + @PreAuthorize |
| **RNF-012** | **Segurança** | Proteção de senhas | Senhas devem ser armazenadas com hash BCrypt (cost factor 10) | BCryptPasswordEncoder |
| **RNF-013** | **Escalabilidade** | Horizontal scaling | Sistema deve suportar múltiplas instâncias sem conflitos | Stateless services, Kafka consumer groups |
| **RNF-014** | **Escalabilidade** | Desacoplamento de serviços | Microsserviços devem comunicar-se de forma assíncrona | Event-Driven Architecture (Kafka) |
| **RNF-015** | **Resiliência** | Circuit Breaker para dependências | Sistema deve abrir circuito após 50% de falhas em Elasticsearch (sliding window de 10 requisições) | Resilience4j Circuit Breaker |
| **RNF-016** | **Resiliência** | Reprocessamento automático | Eventos falhos devem ser reprocessados automaticamente com backoff: 1m → 2m → 4m → 8m → 16m → 32m → 60m | DLQ + Scheduled Reprocessor |
| **RNF-017** | **Resiliência** | Race condition prevention | Operações de estoque devem prevenir condições de corrida (TOCTOU) | Pessimistic Locking (@Lock) |
| **RNF-018** | **Manutenibilidade** | Versionamento de schema | Alterações de banco de dados devem ser versionadas e auditadas | Flyway Migrations |
| **RNF-019** | **Manutenibilidade** | Logs estruturados | Sistema deve gerar logs estruturados com níveis apropriados (INFO, WARN, ERROR) | SLF4J + Logback |
| **RNF-020** | **Observabilidade** | Documentação de API | Sistema deve expor documentação interativa de APIs | Swagger UI (Springdoc OpenAPI) |
| **RNF-021** | **Observabilidade** | Monitoramento de Circuit Breakers | Administradores devem visualizar estado de circuit breakers em tempo real | Admin endpoints + metrics |
| **RNF-022** | **Observabilidade** | Rastreamento de eventos | Sistema deve rastrear eventos processados e falhos com timestamps | ProcessedEvent e FailedEvent entities |
| **RNF-023** | **Testabilidade** | Isolamento de dependências | Controllers devem depender de interfaces para facilitar mocks | Dependency Inversion (SOLID) |
| **RNF-024** | **Portabilidade** | Containerização | Infraestrutura deve ser provisionada via Docker Compose | MySQL, Elasticsearch, Kafka containers |

### Constraints (Restrições)

- **C-001**: Sistema deve usar Java 17 LTS
- **C-002**: Persistência primária deve ser MySQL 8.0 (ACID compliance)
- **C-003**: Eventos críticos (pagamento) devem ter garantia de entrega (At-Least-Once)
- **C-004**: Token JWT deve expirar em no máximo 24 horas
- **C-005**: Migrações de banco de dados devem ser imutáveis (checksum validation)
- **C-006**: Senhas devem ter no mínimo 8 caracteres
- **C-007**: Estoque nunca pode ficar negativo (constraint de negócio)

---

## 🏗️ Arquitetura do Sistema

### Visão Geral

Sistema distribuído com **três microsserviços** comunicando-se via Kafka, garantindo alta disponibilidade e tolerância a falhas.

```
┌─────────────────────────────────────────────────────────────────────┐
│  Main API (Port 8080) - Public & User Operations                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ Rate Limit   │→ │ JWT Auth     │→ │ Circuit      │              │
│  │ Filter       │  │ Filter       │  │ Breaker      │              │
│  └──────────────┘  └──────────────┘  └──────────────┘              │
│         ↓                  ↓                  ↓                      │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  Business Logic (Service Layer)                               │  │
│  │  • OrderService - Pedidos com validação de estoque           │  │
│  │  • ProductService - Catálogo com dual-write prevention       │  │
│  │  • AuthService - JWT + Account Lockout                       │  │
│  └───────────────────────────────────────────────────────────────┘  │
│         ↓                              ↓                            │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ MySQL       │  │ Elasticsearch │  │ Outbox Table │              │
│  │ (ACID)      │  │ (Search)      │  │ (Events)     │              │
│  └─────────────┘  └──────────────┘  └──────────────┘              │
│                                             ↓                       │
│                              ┌─────────────────────────┐            │
│                              │ Kafka Producer          │            │
│                              │ (Transactional Outbox)  │            │
│                              └─────────────────────────┘            │
└─────────────────────────────────────────────────────────────────────┘
                                       │
                          Kafka Topics: order.paid
                                       product.sync
                                       │
┌─────────────────────────────────────────────────────────────────────┐
│  Consumer (Port 8081) - Async Stock & Sync                         │
│  • OrderEventConsumer - Stock reduction after payment               │
│  • ProductSyncConsumer - MySQL ↔ Elasticsearch consistency         │
│  • DLQ Reprocessor - Automatic retry with exponential backoff      │
└─────────────────────────────────────────────────────────────────────┘
```

### Stack Tecnológico

| Camada | Tecnologia | Versão | Propósito |
|--------|------------|--------|-----------|
| **Runtime** | Java | 17 | LTS com performance otimizada |
| **Framework** | Spring Boot | 3.2.0 | Foundation para microservices |
| **Persistência** | MySQL | 8.0 | ACID transactions |
| **Schema Versioning** | Flyway | 9.x | Database migrations |
| **Search Engine** | Elasticsearch | 8.11.0 | Full-text search, fuzzy matching |
| **Mensageria** | Apache Kafka | 2.8+ | Event streaming |
| **Segurança** | Spring Security + JWT | JJWT 0.11.5 | Autenticação stateless |
| **Resiliência** | Resilience4j | 2.1.0 | Circuit breaker, fallbacks |
| **Rate Limiting** | Bucket4j | 8.7.0 | Token bucket algorithm |
| **Cache** | Caffeine | Latest | In-memory caching |
| **Documentação** | Springdoc OpenAPI | 2.2.0 | Swagger UI |

---

## 🚀 Quick Start

### Pré-requisitos
- Java 17+ (OpenJDK ou Oracle)
- Maven 3.6+
- Docker & Docker Compose

### 1. Iniciar Infraestrutura
```bash
cd case-ecommerce-microservice
docker-compose up -d

# Verificar saúde dos serviços
docker-compose ps
```

**Serviços iniciados:**
- MySQL (3306) - ACID transactions
- Elasticsearch (9200) - Full-text search
- Kafka (9092) - Event streaming
- Zookeeper (2181) - Kafka coordination
- Kafka UI (8090) - Kafka management

### 2. Build & Run
```bash
# Build
mvn clean package

# Run (porta 8080)
mvn spring-boot:run
```

**Endpoints:**
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI Spec: http://localhost:8080/v3/api-docs

### 3. Iniciar Consumer
```bash
cd ../case-ecommerce-consumer
mvn clean package
mvn spring-boot:run  # Porta 8081
```

---

## 🧪 Considerações e Instruções de Teste

### Endpoints Disponíveis na API

**Autenticação** (`/api/v1/auth`):
- `POST /login` - Login
- `POST /register` - Registro de usuário

**Produtos** (`/api/v1/products`):
- `GET /` - Listar produtos (com paginação: page, size, sort)
- `GET /{id}` - Buscar produto por ID
- `GET /search` - Buscar com filtros (name, category, priceMin, priceMax)
- `POST /` - Criar produto (ADMIN)
- `PUT /{id}` - Atualizar produto (ADMIN)
- `DELETE /{id}` - Deletar produto (ADMIN)

**Pedidos** (`/api/v1/orders`):
- `GET /` - Listar pedidos do usuário
- `GET /{id}` - Buscar pedido por ID
- `POST /` - Criar pedido
- `POST /{id}/pay` - Confirmar pagamento

**Relatórios** (`/api/v1/reports` - ADMIN apenas):
- `GET /top-users` - Top 5 usuários compradores (com filtro de datas: startDate, endDate)
- `GET /average-ticket` - Ticket médio por usuário (com filtro de datas)
- `GET /current-month-revenue` - Receita do mês atual

---

### 📚 Documentação de Testes

Este projeto possui endpoints específicos implementados. Para exemplos detalhados de requisições curl com todos os endpoints disponíveis, consulte o arquivo [TESTING_EXAMPLES.md](./docs/TESTING_EXAMPLES.md).

- [QA_TEST_REPORT.md](./docs/QA_TEST_REPORT.md) - Relatório completo de testes executados
- [ADVANCED_TESTS_REPORT.md](./docs/ADVANCED_TESTS_REPORT.md) - Testes avançados (resiliência, segurança)

---

## 🔒 Segurança em Profundidade

### ⚠️ AVISO: Configuração de Produção

**IMPORTANTE**: Este é um projeto educacional. Para **produção**, externalize credenciais:

```yaml
# application-prod.yml
app:
  jwt:
    secret: ${JWT_SECRET}  # Variável de ambiente

spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

elasticsearch:
  uris: ${ELASTICSEARCH_URL}
  username: ${ELASTICSEARCH_USERNAME}
  password: ${ELASTICSEARCH_PASSWORD}
```

Consulte [SECURITY_AUDIT_REPORT.md](../SECURITY_AUDIT_REPORT.md) para detalhes completos.

---

### Camadas de Segurança

#### 1. Rate Limiting (Primeira Linha de Defesa)

**Algoritmo:** Token Bucket (Bucket4j)
**Posição:** ANTES do JwtAuthenticationFilter

| Tipo | Limite | Janela | Proteção |
|------|--------|--------|----------|
| `AUTH` | 5 req/min | Brute force em login |
| `PUBLIC` | 100 req/min | DDoS, scraping |
| `USER` | 30 req/min | Abuso de recursos autenticados |
| `ADMIN` | 60 req/min | Insider threat |
| `SEARCH` | 60 req/min | Elasticsearch query abuse |
| `REPORT` | 10 req/min | DB overload (agregações pesadas) |

**Key Strategy:**
- Endpoints públicos: IP address
- Endpoints autenticados: User ID (JWT claim)

**Headers de Resposta:**
```http
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1735987200000
Retry-After: 60  # quando bloqueado (HTTP 429)
```

#### 2. Autenticação JWT (Stateless)

**Configuração:**
- Algoritmo: HS512 (512-bit secret)
- Expiração: 24 horas
- Claims: userId, email, role, iat, exp

**Fluxo:**
```
1. POST /api/v1/auth/login
   → BCrypt password verification
2. Token generation (HS512)
3. Response: { token, type: "Bearer", user }
4. Requests: Authorization: Bearer {token}
5. JwtAuthenticationFilter validates token
```

**Proteções Implementadas:**
- Senhas com BCrypt (cost factor 10)
- **Password Complexity Validation**: 12+ chars, uppercase, lowercase, numbers, special chars
- **Account Lockout**: 5 tentativas → 30 min bloqueio (Caffeine cache)
- Secret rotation via config externa (não hardcoded)

#### 3. Autorização RBAC (Role-Based Access Control)

**Roles:**
- `ADMIN` - Full CRUD, relatórios, monitoramento
- `USER` - Pedidos próprios, visualização de produtos

**Implementação:**
```java
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> adminEndpoint() { ... }

// Ownership validation
public OrderResponse getOrderByIdForUser(UUID orderId, User currentUser) {
    Order order = orderRepository.findByIdWithUser(orderId);
    if (!order.getUser().equals(currentUser)) {
        throw new BusinessException("Access denied", HttpStatus.FORBIDDEN);
    }
    return orderMapper.toResponse(order);
}
```

#### 4. Proteções Adicionais

✅ **SQL Injection**: JPA com prepared statements
✅ **XSS**: JSON serialization sem HTML rendering
✅ **CSRF**: Não aplicável (API stateless)
✅ **Sensitive Data**: Passwords com `@JsonIgnore`
✅ **Idempotency Keys**: Previne pedidos duplicados (V3 migration)
✅ **Stack Trace Sanitization**: Erros genéricos em produção

---

## 🛡️ Resiliência e Consistência

### Circuit Breaker Pattern (Resilience4j)

Previne **cascading failures** quando dependências externas falham.

#### Configurações por Serviço

**1. Elasticsearch (AGRESSIVO - tem fallback MySQL)**
```yaml
failure-rate-threshold: 50%       # 50% falhas → OPEN
slow-call-duration-threshold: 3s
wait-duration-in-open-state: 30s
sliding-window-size: 10
```

**Fallback Strategy:**
```java
@CircuitBreaker(name = "elasticsearch", fallbackMethod = "searchFallback")
public List<ProductResponse> searchByName(String name) {
    return elasticsearchRepo.findByNomeWithFuzziness(name);
}

private List<ProductResponse> searchFallback(String name, Throwable t) {
    log.warn("⚠️ ES circuit OPEN, using MySQL fallback");
    return mysqlRepo.findByNomeContainingIgnoreCase(name);
}
```

**Trade-off**: Perde fuzzy matching, mas mantém disponibilidade.

**2. Kafka (MODERADO - eventos críticos)**
```yaml
failure-rate-threshold: 60%
wait-duration-in-open-state: 60s
sliding-window-size: 20
```
- Aplicado em: `OutboxPublisher.publishEvent()`
- Proteção: Evita thread pool exhaustion

**3. MySQL (CONSERVADOR - datasource primário)**
```yaml
failure-rate-threshold: 70%
slow-call-duration-threshold: 2s
record-exceptions:
  - DataAccessResourceFailureException
  - CannotGetJdbcConnectionException
  - SQLException
ignore-exceptions:
  - ResourceNotFoundException  # → HTTP 404
  - BusinessException          # → HTTP 400/403
```
- **CRÍTICO**: Apenas exceções de infraestrutura trigam o breaker
- Exceções de negócio passam para GlobalExceptionHandler

**Monitoramento:**
```bash
GET /api/v1/admin/circuit-breakers/status
```

---

### Transactional Outbox Pattern

**Problema:** Dual-write antipattern
```java
// ❌ PROBLEMA: Event loss on Kafka failure
@Transactional
public void payOrder(UUID orderId) {
    orderRepository.save(order);        // ✅ MySQL commit
    kafkaTemplate.send("order.paid");   // ❌ Kafka down - EVENT LOST!
}
```

**Solução:** Outbox table com ACID guarantees

#### Arquitetura
```
┌──────────────────────────────────────────┐
│ @Transactional (ACID)                    │
│ ┌──────────────────────────────────────┐ │
│ │ 1. UPDATE orders SET status='PAGO'  │ │
│ │ 2. INSERT INTO outbox_events (...)  │ │
│ └──────────────────────────────────────┘ │
│              ⬇ COMMIT                    │
└──────────────────────────────────────────┘
              ⬇
┌──────────────────────────────────────────┐
│ OutboxPublisher (@Scheduled 5s)          │
│ 1. SELECT * FROM outbox_events           │
│    WHERE published = false               │
│ 2. kafkaTemplate.send(event)             │
│ 3. UPDATE published = true               │
└──────────────────────────────────────────┘
```

**Garantias:**
| Garantia | Descrição |
|----------|-----------|
| **Atomicity** | Evento salvo na mesma TX (ACID) |
| **Durability** | Sobrevive a crashes |
| **At-Least-Once** | Retry infinito com backoff |
| **Idempotency** | Consumer verifica duplicatas |
| **Ordering** | Kafka partition key = aggregateId |

**Uso Correto:**
```java
@Transactional  // ⬅️ Necessário para MANDATORY propagation
public PaymentResponse payOrder(UUID orderId, User user) {
    order.markAsPaid();
    orderRepository.save(order);

    // MESMA transação
    outboxService.saveEvent("ORDER", orderId.toString(), "ORDER_PAID",
                           orderEvent, AppConstants.TOPIC_ORDER_PAID);

    return new PaymentResponse("Pagamento confirmado");
}
```

---

### Dead Letter Queue (DLQ) + Automatic Reprocessing

**Problema:** Eventos falham quando Elasticsearch está offline

**Solução:** Consumer armazena eventos falhos em `failed_events` table e reprocessa automaticamente com **exponential backoff**.

**Fluxo:**
```
Kafka Event → Consumer Fails (3 retries) → DLQ Topic
                                              ↓
                                    DeadLetterQueueConsumer
                                              ↓
                                    failed_events table
                                              ↓
                             FailedEventReprocessor (@Scheduled 2min)
                                              ↓
                             Exponential Backoff:
                             1m → 2m → 4m → 8m → 16m → 32m → 60m
```

**Garantias:**
✅ Eventos sobrevivem a restarts
✅ Consistência eventual quando serviços recuperam
✅ Backpressure control (evita overload)
✅ Circuit Breaker protection

Veja [case-ecommerce-consumer/README.md](../case-ecommerce-consumer/README.md) para detalhes.

---

## 📊 Consistência de Dados

### Dual-Repository Pattern (MySQL + Elasticsearch)

**Repositórios separados** para evitar conflitos Spring Data:

- **JPA Repositories** (`repository.jpa.*`) - MySQL com ACID
- **Elasticsearch Repositories** (`repository.search.*`) - Full-text search

**Configuração:**
```java
@EnableJpaRepositories(basePackages = "com.foursales.ecommerce.repository.jpa")
@EnableElasticsearchRepositories(basePackages = "com.foursales.ecommerce.repository.search")
```

**IMPORTANTE:** NUNCA misturar anotações JPA e ES na mesma entidade.

### Product Sync via Event Sourcing

**Problema:** Produto criado no MySQL não aparece no Elasticsearch

**Solução:** Event-driven sync com idempotência

**Fluxo:**
```
1. ProductService.createProduct()
   - Salva MySQL (@Transactional)
   - Publica ProductSyncEvent via Outbox
2. OutboxPublisher → Kafka (topic: product.sync)
3. ProductSyncEventConsumer:
   - Verifica idempotência (processed_events table)
   - Se não processado:
     a) Sincroniza com Elasticsearch
     b) Registra evento como processado
```

**Idempotência:**
```sql
CREATE TABLE processed_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,  -- UUID
    event_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,
    INDEX idx_event_id (event_id)
);
```

---

## 🗄️ Database Migrations (Flyway)

### Estratégia

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # ⚠️ APENAS valida, NÃO modifica schema
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    validate-on-migrate: true
```

### Migrations Aplicadas

**V1__initial_schema.sql** - Schema base
- `users` - Autenticação JWT, roles (ADMIN/USER)
- `products` - Catálogo com estoque
- `orders` - Pedidos com status (PENDENTE, PAGO, CANCELADO)
- `order_items` - Itens com quantidade e preço unitário
- `outbox_events` - Transactional Outbox

**V2__add_performance_indexes.sql** - Performance
- 13 índices estratégicos (10-100x speedup)
- `idx_outbox_published_created` - Outbox query optimization
- `idx_orders_user_id_status` - User orders query
- `idx_products_category` - Category filtering

**V3__add_idempotency_key.sql** - Idempotência
- `idempotency_key` column in orders
- UNIQUE constraint (user_id, idempotency_key)
- Previne pedidos duplicados (double-click, retry)

### Adicionar Nova Migration

```bash
# 1. Criar arquivo versionado
# src/main/resources/db/migration/V4__add_feature.sql

# 2. Escrever SQL DDL
ALTER TABLE users ADD COLUMN phone VARCHAR(20);
CREATE INDEX idx_users_phone ON users(phone);

# 3. Restart aplicação (Flyway aplica automaticamente)
mvn spring-boot:run
```

**⚠️ NUNCA modificar migrations executadas** (checksum validation)

---

## 🎯 Boas Práticas Implementadas

### SOLID Principles

**Dependency Inversion:**
```java
// Controllers dependem de INTERFACES
@RequiredArgsConstructor
public class OrderController {
    private final IOrderService orderService;  // ✅ Interface
}
```

**Interfaces:**
- `IOrderService`, `IProductService`, `IReportService`, `IAuthService`
- Facilita testes (mocks), permite múltiplas implementações


**Pessimistic Locking** (Race Condition Fix):
```java
// Previne TOCTOU em stock operations
@Lock(PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdForUpdate(@Param("id") UUID id);
```

**HikariCP Tuning:**
```yaml
hikari:
  minimum-idle: 10
  maximum-pool-size: 20
  connection-timeout: 30000
  leak-detection-threshold: 60000
  max-lifetime: 1800000
```

---

## 🛠️ Monitoramento

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Circuit Breakers**: `GET /api/v1/admin/circuit-breakers/status`
- **Outbox Stats**: `GET /api/v1/admin/outbox/stats`
- **Rate Limit Stats**: `GET /api/v1/admin/rate-limit/stats`
- **Elasticsearch Indices**: http://localhost:9200/_cat/indices
- **Kafka UI**: http://localhost:8090

---

## 🐛 Troubleshooting

### Circuit Breaker Always Open
```bash
# Verificar saúde do serviço
curl http://localhost:9200/_cluster/health

# Forçar transição para CLOSED
curl -X POST http://localhost:8080/api/v1/admin/circuit-breakers/elasticsearch/transition?targetState=CLOSED \
  -H "Authorization: Bearer {admin-token}"
```

### Outbox Events Stuck
```bash
# Ver eventos presos
curl http://localhost:8080/api/v1/admin/outbox/stuck \
  -H "Authorization: Bearer {admin-token}"

# Retry manual
curl -X POST http://localhost:8080/api/v1/admin/outbox/retry/{id} \
  -H "Authorization: Bearer {admin-token}"
```

### Elasticsearch Date Format Error
```bash
curl -X DELETE "localhost:9200/products"
# Restart para recriar índice
```

---

## 📖 Documentação Adicional

- [CLAUDE.md](../CLAUDE.md) - Guia completo para IA assistants
- [RATE_LIMITING_GUIDE.md](./docs/RATE_LIMITING_GUIDE.md) - Detalhes de rate limiting
- [ADVANCED_TESTS_REPORT.md](./docs/ADVANCED_TESTS_REPORT.md) - Detalhes dos Testes avançados
- [QA_TEST_REPORT.md](./docs/QA_TEST_REPORT.md) - Relatorio geral de testes executados

---

### Proposta de evolução
- Implementar mecanismos de tracing distribuído. Sugestão é delegar para camada de infraestrutura utilizando soluções como OpenTelemetry, por exemplo.
- Implementar estratégias de cache na consulta de produtos.
- Separar a consulta da escrita utilizando padrão como CQRS, por exemplo.
---

**Versão**: 1.0.0
**Última Atualização**: Outubro 2025
**Desenvolvido como desafio técnico para Foursales**

### Destaques Técnicos

✅ **Resiliência**: Circuit Breaker, Retry, Fallbacks, DLQ
✅ **Consistência**: Transactional Outbox, Event Sourcing, Idempotency
✅ **Segurança**: Rate Limiting, JWT, RBAC, Account Lockout, Password Complexity
✅ **Performance**: Pessimistic Locking, N+1 Fix, HikariCP, Indexes
✅ **Observabilidade**: Logs, Metrics, Admin endpoints, Swagger UI
✅ **Boas Práticas**: SOLID, Clean Code, Flyway Migrations

### Considerações finais
Para desenvolvimento do projeto, foram utilizados recursos de IA para:
 - Acelerar o desenvolvimento
 - Auxilio na elaboração da documentação
 - Execução, simulação e geração dos relatórios de testes
 - Exploração de vulnerabilidades de segurança e sugestões de melhorias. (simulando ambiente real)

**Importante** O uso de IA ajuda a potencializar o desenvolvimento mas não diminui a autoria.

