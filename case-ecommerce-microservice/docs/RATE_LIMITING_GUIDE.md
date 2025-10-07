# Guia de Rate Limiting - E-commerce Foursales

## 📋 Visão Geral

O sistema implementa rate limiting usando **Bucket4j** com estratégia de **Token Bucket**, protegendo todos os endpoints da API contra:
- ✅ Ataques DDoS
- ✅ Força bruta em autenticação
- ✅ Scraping de dados
- ✅ Abuso de recursos (Elasticsearch, MySQL, Kafka)
- ✅ Enumeração de pedidos

---

## 🔧 Configuração de Limites por Endpoint

### 1. **Endpoints Públicos** (100 req/min por IP)
```
GET  /api/v1/products
GET  /api/v1/products/{id}
GET  /api/v1/products?page=0&size=20
```

**Estratégia:** Limita por **IP** (mesmo sem autenticação)

**Cenário protegido:**
- Bot fazendo scraping de catálogo
- Crawler excessivo
- Ataque de enumeração de produtos

---

### 2. **Endpoints de Autenticação** (5 req/min por IP) 🔴 CRÍTICO
```
POST /api/v1/auth/login
POST /api/v1/auth/register
```

**Estratégia:** Limita por **IP** (antes da autenticação)

**Cenário protegido:**
- Força bruta de senhas
- Criação massiva de contas
- Tentativa de descobrir usuários válidos

**Exemplo de ataque bloqueado:**
```bash
# Atacante tenta 100 senhas
for i in {1..100}; do
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"admin@foursales.com\",\"password\":\"senha$i\"}"
done

# Resultado:
# Requisições 1-5: Testadas normalmente
# Requisição 6+: HTTP 429 Too Many Requests
```

---

### 3. **Endpoints de Pedidos** (30 req/min por usuário)
```
POST /api/v1/orders
POST /api/v1/orders/{id}/pay
GET  /api/v1/orders
GET  /api/v1/orders/{id}
```

**Estratégia:** Limita por **User ID** (após autenticação)

**Cenário protegido:**
- Usuário criando pedidos em massa (DoS)
- Bot automatizado fazendo compras fraudulentas
- Reserva maliciosa de estoque

---

### 4. **Endpoints de Busca** (60 req/min por IP)
```
GET  /api/v1/products/search?nome=notebook
GET  /api/v1/products/search?categoria=eletronicos
GET  /api/v1/products/search/advanced?precoMin=100&precoMax=500
```

**Estratégia:** Limita por **IP**

**Cenário protegido:**
- Queries excessivas no Elasticsearch (CARO!)
- Scraping de produtos por categoria
- Sobrecarga do cluster Elasticsearch

**Custo evitado:**
```
Sem rate limiting:
- Bot: 1000 req/min × 60 min = 60.000 queries/hora
- Custo Elasticsearch: ~$0.10 por 1000 queries = $6/hora = $144/dia

Com rate limiting (60 req/min):
- Máximo: 60 × 60 = 3.600 queries/hora
- Custo: $0.36/hora = $8.64/dia
- ECONOMIA: $135/dia = $4.050/mês 💰
```

---

### 5. **Endpoints Admin** (60 req/min por admin)
```
POST   /api/v1/products
PUT    /api/v1/products/{id}
DELETE /api/v1/products/{id}
```

**Estratégia:** Limita por **User ID** (apenas admin autenticado)

**Cenário protegido:**
- Admin comprometido fazendo operações em massa
- Script automatizado deletando produtos
- Proteção contra insider threat

---

### 6. **Endpoints de Relatórios** (10 req/min por admin) 🔴 CRÍTICO
```
GET /api/v1/reports/top-users
GET /api/v1/reports/average-ticket
GET /api/v1/reports/current-month-revenue
```

**Estratégia:** Limita por **User ID** (apenas admin)

**Cenário protegido:**
- Queries complexas com JOIN e agregação
- Sobrecarga do banco de dados
- Mesmo admin pode causar DoS involuntário

**Por que limitar admin?**
```sql
-- Query de relatório pode levar 5-10 segundos
SELECT u.id, u.name, COUNT(o.id) as order_count
FROM users u
JOIN orders o ON u.id = o.user_id
GROUP BY u.id
ORDER BY COUNT(o.id) DESC;

-- Admin fazendo 100 requisições simultâneas:
-- = 100 conexões no pool (MAX: 100)
-- = Pool esgotado = SISTEMA INDISPONÍVEL para todos!
```

---

## 📊 Testando Rate Limiting

### Teste 1: Endpoint Público (100 req/min)

```bash
# Fazer 105 requisições rapidamente
for i in {1..105}; do
  echo "Request $i:"
  curl -s -o /dev/null -w "HTTP %{http_code}\n" \
    http://localhost:8080/api/v1/products
done

# Resultado esperado:
# Requests 1-100: HTTP 200
# Requests 101-105: HTTP 429 (Rate limit exceeded)
```

**Headers de resposta (requisição 100):**
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1735987200000
```

**Headers de resposta (requisição 101 - bloqueada):**
```
HTTP/1.1 429 Too Many Requests
Retry-After: 60
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1735987200000

{
  "code": 429,
  "message": "Rate limit exceeded",
  "stackTrace": "Too many requests. Limit: 100 requests per 60 seconds. Please try again later.",
  "path": "/api/v1/products",
  "timestamp": "2025-10-04T14:30:00"
}
```

---

### Teste 2: Login (5 req/min) - Proteção Brute Force

```bash
# Script de brute force (tentando 10 senhas)
for i in {1..10}; do
  echo "Tentativa $i:"
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"admin@foursales.com\",\"password\":\"senha$i\"}" \
    -w "HTTP %{http_code}\n"
  sleep 0.5
done

# Resultado esperado:
# Tentativas 1-5: HTTP 401 (Unauthorized - senha incorreta)
# Tentativas 6-10: HTTP 429 (Too Many Requests - bloqueado!)
```

**Logs do servidor (request 6):**
```
WARN  [RateLimitFilter] Rate limit exceeded for ip:192.168.1.100 - Path: /api/v1/auth/login, Type: AUTH
```

---

### Teste 3: Criação de Pedidos (30 req/min por usuário)

```bash
# Login primeiro
TOKEN=$(curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@test.com","password":"password123"}' \
  | jq -r '.token')

# Tentar criar 35 pedidos
for i in {1..35}; do
  echo "Order $i:"
  curl -X POST http://localhost:8080/api/v1/orders \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "items": [
        {"productId": "uuid-produto-1", "quantity": 1}
      ]
    }' \
    -w "HTTP %{http_code}\n"
done

# Resultado esperado:
# Orders 1-30: HTTP 201 (Created)
# Orders 31-35: HTTP 429 (Too Many Requests)
```

---

### Teste 4: Diferentes IPs (rate limiting independente)

```bash
# IP 1 faz 100 requisições
curl http://localhost:8080/api/v1/products  # OK (IP: 192.168.1.100)

# IP 2 também consegue fazer 100 requisições (bucket diferente)
curl -H "X-Forwarded-For: 192.168.1.200" \
  http://localhost:8080/api/v1/products  # OK (IP: 192.168.1.200)
```

**Rate limiting é isolado por chave!**
- `ip:192.168.1.100:PUBLIC` → bucket separado
- `ip:192.168.1.200:PUBLIC` → bucket separado
- `user:uuid-123:USER` → bucket separado

---

## 🛠️ Endpoints de Administração

### Ver Configuração de Limites
```bash
curl http://localhost:8080/api/v1/admin/rate-limit/config \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# Resposta:
{
  "PUBLIC": {
    "limit": 100,
    "window": "1 minute",
    "description": "Public endpoints (product listing, search)"
  },
  "AUTH": {
    "limit": 5,
    "window": "1 minute",
    "description": "Authentication endpoints (login, register)"
  },
  ...
}
```

### Ver Estatísticas do Cache
```bash
curl http://localhost:8080/api/v1/admin/rate-limit/stats \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# Resposta:
{
  "cacheStats": "Rate Limit Cache - Size: 1523, Hit Rate: 94.32%",
  "status": "active"
}
```

### Limpar Todos os Buckets (resetar limites)
```bash
curl -X POST http://localhost:8080/api/v1/admin/rate-limit/clear \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq

# Resposta:
{
  "message": "All rate limit buckets cleared successfully"
}
```

---

## 📈 Monitoramento e Alertas

### Logs de Rate Limit Exceeded

```
2025-10-04 14:30:00 WARN  [RateLimitFilter] Rate limit exceeded for ip:192.168.1.100 - Path: /api/v1/auth/login, Type: AUTH
2025-10-04 14:30:05 WARN  [RateLimitFilter] Rate limit exceeded for user:uuid-123 - Path: /api/v1/orders, Type: USER
```

### Métricas para Grafana/Prometheus

**Adicionar contadores customizados:**
```java
// No RateLimitService
@Autowired
private MeterRegistry meterRegistry;

public boolean tryConsume(String key, RateLimitType rateLimitType) {
    boolean consumed = bucket.tryConsume(1);

    if (!consumed) {
        meterRegistry.counter("rate_limit.exceeded",
            "type", rateLimitType.name()).increment();
    }

    return consumed;
}
```

---

## 🔍 Troubleshooting

### Problema: "Estou sendo bloqueado mesmo com poucas requisições"

**Causa:** Você pode estar compartilhando IP (NAT, proxy)

**Solução:** Autenticar-se para ter limite baseado em User ID
```bash
# Antes (limitado por IP compartilhado):
curl http://localhost:8080/api/v1/products  # Pode atingir limite rápido

# Depois (limitado por seu usuário):
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/products  # Limite independente
```

---

### Problema: "Rate limit não está funcionando"

**Verificar:**
1. Filtro está registrado no SecurityConfig?
2. Bucket4j está no classpath? (`mvn dependency:tree | grep bucket4j`)
3. Logs mostram rate limit warnings?

**Debug:**
```bash
# Ativar logs debug
# application.yml
logging:
  level:
    com.foursales.ecommerce.ratelimit: DEBUG
```

---

## 🚀 Ajustando Limites para Produção

### Cenários de Ajuste

**E-commerce pequeno (<1000 usuários):**
```java
PUBLIC(100, Duration.ofMinutes(1))   // OK
AUTH(5, Duration.ofMinutes(1))       // OK
USER(30, Duration.ofMinutes(1))      // OK
```

**E-commerce médio (1000-10000 usuários):**
```java
PUBLIC(200, Duration.ofMinutes(1))   // Aumentar
AUTH(10, Duration.ofMinutes(1))      // Aumentar (mas cuidado!)
USER(50, Duration.ofMinutes(1))      // Aumentar
SEARCH(100, Duration.ofMinutes(1))   // Aumentar
```

**E-commerce grande (>10000 usuários):**
```java
// Considerar implementar rate limiting distribuído (Redis)
// Bucket4j suporta Redis como backend
```

---

## ⚠️ Considerações de Segurança

### Bypass de Rate Limiting (prevenção)

**❌ Não confiar apenas em IP:**
```
Atacante pode rotacionar IPs (VPN, proxies, botnets)
Solução: Rate limiting combinado (IP + User + Session)
```

**❌ Não remover rate limiting de admin:**
```
Admin comprometido = maior poder de ataque
Solução: Manter limites mesmo para admin (mas mais altos)
```

**✅ Rate limiting é primeira linha de defesa:**
```
Rate Limiting → WAF → Load Balancer → Application
```

---

## 📚 Arquitetura da Solução

```
┌─────────────────────────────────────────────────────┐
│                   HTTP Request                       │
└────────────────────┬────────────────────────────────┘
                     │
                     ▼
         ┌───────────────────────┐
         │   RateLimitFilter     │ ◄── Executa ANTES de tudo
         │   (OncePerRequestFilter)│
         └───────────┬───────────┘
                     │
                ┌────▼─────┐
                │ Allowed? │
                └──┬───┬───┘
                   │   │
              YES  │   │  NO
                   │   │
                   │   └──► HTTP 429 + Retry-After
                   │         + Rate limit headers
                   ▼
         ┌───────────────────────┐
         │ JwtAuthenticationFilter│
         └───────────┬───────────┘
                     │
                     ▼
         ┌───────────────────────┐
         │   Security Filters    │
         └───────────┬───────────┘
                     │
                     ▼
         ┌───────────────────────┐
         │      Controller       │
         └───────────────────────┘
```

### Componentes

1. **RateLimitType** (Enum)
   - Define limites por tipo de endpoint
   - Retorna configuração Bandwidth

2. **RateLimitService** (Service)
   - Gerencia buckets usando Caffeine Cache
   - Cache de 100k buckets, expira após 10min inatividade
   - Métodos: `tryConsume()`, `getRemainingTokens()`

3. **RateLimitKeyResolver** (Component)
   - Resolve chave única: `ip:x.x.x.x` ou `user:uuid`
   - Extrai IP considerando proxies (X-Forwarded-For)
   - Determina tipo de rate limit baseado no path

4. **RateLimitFilter** (Filter)
   - Intercepta requisições ANTES de autenticação
   - Adiciona headers informativos (X-RateLimit-*)
   - Retorna 429 com Retry-After quando excedido

5. **RateLimitController** (Controller)
   - Endpoints admin para monitoramento
   - Estatísticas do cache
   - Limpar buckets manualmente

---

## ✅ Checklist de Implementação

- [x] Dependência Bucket4j adicionada
- [x] Enum RateLimitType criado com 6 tipos
- [x] RateLimitService implementado com Caffeine
- [x] RateLimitKeyResolver para IP e User
- [x] RateLimitFilter integrado ao SecurityFilterChain
- [x] Headers X-RateLimit-* adicionados
- [x] Retry-After header em 429 responses
- [x] Endpoints admin para monitoramento
- [x] Logs de warning quando limite excedido
- [x] Constantes movidas para AppConstants

---

## 🎯 Resultados Esperados

**Antes do Rate Limiting:**
- ❌ Vulnerável a DDoS
- ❌ Força bruta em login ilimitada
- ❌ Bots podem scraper todo catálogo
- ❌ Custos de infra imprevisíveis
- ❌ Sistema pode cair por abuso

**Depois do Rate Limiting:**
- ✅ Proteção contra DDoS (IP-based)
- ✅ Brute force limitado a 5 tentativas/min
- ✅ Bots limitados a 100 req/min
- ✅ Custos previsíveis e controlados
- ✅ Sistema estável mesmo sob ataque

---

**Data:** 2025-10-04
**Versão:** 1.0.0
**Autor:** Claude Code
