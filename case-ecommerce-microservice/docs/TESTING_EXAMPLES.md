# 🧪 Exemplos de Teste da API

Exemplos de requisições curl baseados nos endpoints **reais** implementados.

## Endpoints Disponíveis

### Autenticação (`/api/v1/auth`)
- `POST /login` - Login
- `POST /register` - Registro de usuário

### Produtos (`/api/v1/products`)
- `GET /` - Listar produtos (com paginação)
- `GET /{id}` - Buscar por ID
- `GET /search` - Buscar com filtros (name, category, priceMin, priceMax)
- `POST /` - Criar produto (ADMIN)
- `PUT /{id}` - Atualizar produto (ADMIN)
- `DELETE /{id}` - Deletar produto (ADMIN)

### Pedidos (`/api/v1/orders`)
- `GET /` - Listar pedidos do usuário
- `GET /{id}` - Buscar pedido por ID
- `POST /` - Criar pedido
- `POST /{id}/pay` - Confirmar pagamento

### Relatórios (`/api/v1/reports` - ADMIN)
- `GET /top-users` - Top 5 usuários compradores (com filtros de data)
- `GET /average-ticket` - Ticket médio por usuário (com filtros de data)
- `GET /current-month-revenue` - Receita do mês atual

---

## 1. Autenticação

### Registrar Novo Usuário
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "João Silva",
    "email": "joao@test.com",
    "password": "SecurePass123!@",
    "role": "USER"
  }'
```

### Login e Obter Token
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "joao@test.com",
    "password": "SecurePass123!@"
  }' | jq -r '.token')

echo "Token: $TOKEN"
```

### Login Admin
```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@ecommerce.com",
    "password": "Admin123!@#"
  }' | jq -r '.token')
```

---

## 2. Busca de Produtos com Fuzzy Matching

### Busca por Nome (Fuzzy)
```bash
# Busca exata
curl "http://localhost:8080/api/v1/products/search?name=Notebook"

# Fuzzy: "Notbook" encontra "Notebook"
curl "http://localhost:8080/api/v1/products/search?name=Notbook"

# Fuzzy: "Mous" encontra "Mouse"
curl "http://localhost:8080/api/v1/products/search?name=Mous"
```

### Busca por Categoria
```bash
# Apenas categoria
curl "http://localhost:8080/api/v1/products/search?category=ELETRONICOS"

# Nome + Categoria
curl "http://localhost:8080/api/v1/products/search?name=Samsung&category=ELETRONICOS"
```

### Busca por Faixa de Preço
```bash
# Preço mínimo
curl "http://localhost:8080/api/v1/products/search?priceMin=1000"

# Preço máximo
curl "http://localhost:8080/api/v1/products/search?priceMax=5000"

# Faixa de preço
curl "http://localhost:8080/api/v1/products/search?priceMin=1000&priceMax=5000"

# Combinando todos os filtros
curl "http://localhost:8080/api/v1/products/search?name=Notebook&category=ELETRONICOS&priceMin=2000&priceMax=8000"
```

### Teste de Fallback (Elasticsearch Offline)
```bash
# 1. Derrubar Elasticsearch
docker-compose stop elasticsearch

# 2. Buscar (Circuit Breaker ativa fallback MySQL)
curl "http://localhost:8080/api/v1/products/search?name=Notebook"

# 3. Reativar
docker-compose start elasticsearch
```

---

## 3. Listagem de Produtos com Paginação

```bash
# Página 0, tamanho 10 (padrão)
curl "http://localhost:8080/api/v1/products?page=0&size=10"

# Página 1, 20 itens
curl "http://localhost:8080/api/v1/products?page=1&size=20"

# Ordenado por nome (crescente)
curl "http://localhost:8080/api/v1/products?page=0&size=10&sort=nome,asc"

# Ordenado por preço (decrescente)
curl "http://localhost:8080/api/v1/products?page=0&size=10&sort=preco,desc"
```

---

## 4. Gerenciamento de Produtos (Admin)

### Criar Produto
```bash
PRODUCT_ID=$(curl -s -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Notebook Dell Inspiron 15",
    "description": "Intel i7, 16GB RAM, 512GB SSD",
    "price": 3599.90,
    "category": "ELETRONICOS",
    "stockQuantity": 50
  }' | jq -r '.id')
```

### Buscar por ID
```bash
curl "http://localhost:8080/api/v1/products/$PRODUCT_ID"
```

### Atualizar Produto
```bash
curl -X PUT "http://localhost:8080/api/v1/products/$PRODUCT_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Notebook Dell Inspiron 15 - Atualizado",
    "description": "Intel i7, 16GB RAM, 1TB SSD",
    "price": 3799.90,
    "category": "ELETRONICOS",
    "stockQuantity": 45
  }'
```

### Deletar Produto
```bash
curl -X DELETE "http://localhost:8080/api/v1/products/$PRODUCT_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

---

## 5. Gestão de Pedidos

### Criar Pedido
```bash
ORDER_ID=$(curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {
        "productId": "'$PRODUCT_ID'",
        "quantity": 2
      }
    ]
  }' | jq -r '.id')
```

### Criar Pedido com Múltiplos Itens
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {"productId": "uuid-produto-1", "quantity": 2},
      {"productId": "uuid-produto-2", "quantity": 1}
    ]
  }'
```

### Listar Pedidos do Usuário
```bash
curl "http://localhost:8080/api/v1/orders" \
  -H "Authorization: Bearer $TOKEN"
```

### Buscar Pedido por ID
```bash
curl "http://localhost:8080/api/v1/orders/$ORDER_ID" \
  -H "Authorization: Bearer $TOKEN"
```

### Confirmar Pagamento
```bash
curl -X POST "http://localhost:8080/api/v1/orders/$ORDER_ID/pay" \
  -H "Authorization: Bearer $TOKEN"
```

---

## 6. Relatórios com Filtros de Data

### Top 5 Usuários Compradores

```bash
# Sem filtro (todos os pedidos)
curl "http://localhost:8080/api/v1/reports/top-users" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Filtro por período - Formato DATE (YYYY-MM-DD)
curl "http://localhost:8080/api/v1/reports/top-users?startDate=2025-01-01&endDate=2025-01-31" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Filtro com ISO DateTime (YYYY-MM-DDTHH:mm:ss)
curl "http://localhost:8080/api/v1/reports/top-users?startDate=2025-01-01T00:00:00&endDate=2025-01-31T23:59:59" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Último trimestre
curl "http://localhost:8080/api/v1/reports/top-users?startDate=2025-01-01&endDate=2025-03-31" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### Ticket Médio por Usuário

```bash
# Sem filtro
curl "http://localhost:8080/api/v1/reports/average-ticket" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Janeiro 2025
curl "http://localhost:8080/api/v1/reports/average-ticket?startDate=2025-01-01&endDate=2025-01-31" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Último semestre
curl "http://localhost:8080/api/v1/reports/average-ticket?startDate=2025-01-01&endDate=2025-06-30" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### Receita do Mês Atual

```bash
curl "http://localhost:8080/api/v1/reports/current-month-revenue" \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### Formatos de Data Aceitos

Os relatórios aceitam dois formatos:
- **Formato DATE**: `2025-01-01`
- **Formato ISO DateTime**: `2025-01-01T00:00:00`

---

## 7. Teste End-to-End (E2E)

```bash
#!/bin/bash
set -e

echo "=== E2E Test: Complete Purchase Flow ==="

# 1. Registrar usuário
EMAIL="test$(date +%s)@test.com"
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Test User",
    "email": "'$EMAIL'",
    "password": "SecurePass123!@",
    "role": "USER"
  }' | jq '.email'

# 2. Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"'$EMAIL'","password":"SecurePass123!@"}' \
  | jq -r '.token')

# 3. Buscar produtos (fuzzy)
PRODUCTS=$(curl -s "http://localhost:8080/api/v1/products/search?name=Notbook")
PRODUCT_ID=$(echo $PRODUCTS | jq -r '.[0].id')
echo "Produto: $PRODUCT_ID"

# 4. Verificar estoque
INITIAL_STOCK=$(curl -s "http://localhost:8080/api/v1/products/$PRODUCT_ID" | jq '.estoque')
echo "Estoque inicial: $INITIAL_STOCK"

# 5. Criar pedido
ORDER_ID=$(curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"items": [{"productId": "'$PRODUCT_ID'", "quantity": 2}]}' \
  | jq -r '.id')
echo "Pedido: $ORDER_ID"

# 6. Confirmar pagamento
curl -s -X POST "http://localhost:8080/api/v1/orders/$ORDER_ID/pay" \
  -H "Authorization: Bearer $TOKEN" | jq '.message'

# 7. Aguardar processamento (Kafka → Consumer)
sleep 8

# 8. Verificar estoque final
FINAL_STOCK=$(curl -s "http://localhost:8080/api/v1/products/$PRODUCT_ID" | jq '.estoque')
echo "Estoque final: $FINAL_STOCK"

# 9. Validar
EXPECTED=$((INITIAL_STOCK - 2))
if [ "$FINAL_STOCK" -eq "$EXPECTED" ]; then
  echo "✅ SUCESSO"
else
  echo "❌ FALHA"
  exit 1
fi
```

---

### 6. Testes de Performance (Opcional)

**Objetivo**: Validar throughput, latência e comportamento sob carga.

**Ferramentas:** JMeter, Gatling, ou Apache Bench

**Cenário: Rate Limiting**
```bash
# 100 requisições em 1 segundo (deve bloquear após 100)
ab -n 150 -c 10 -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/products

# Esperado: ~100 HTTP 200, ~50 HTTP 429
```

**Cenário: Throughput de Pedidos**
```bash
# 1000 pedidos concorrentes
ab -n 1000 -c 50 -p order.json -T application/json \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/orders

# Meta: P95 < 500ms, Taxa de sucesso > 99%
```

---

### 7. Testes de Segurança

**A. Proteção contra SQL Injection**
```bash
# Tentativa de SQL Injection
curl -X GET "http://localhost:8080/api/v1/products/search?name='; DROP TABLE products;--"

# Esperado: Sem erro, query tratada como string literal
```

**B. Validação de JWT**
```bash
# Token expirado
curl -X GET http://localhost:8080/api/v1/orders \
  -H "Authorization: Bearer expired.jwt.token"

# Esperado: HTTP 401 Unauthorized
```

**C. Account Lockout**
```bash
# 5 tentativas de login falhadas
for i in {1..5}; do
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -d '{"email":"user@test.com","password":"wrong"}'
done

# 6ª tentativa (deve bloquear)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -d '{"email":"user@test.com","password":"correct"}'

# Esperado: HTTP 429 Too Many Requests
```

----

## Observações Importantes

2. **Paginação**: O endpoint `/api/v1/products` suporta paginação via parâmetros `page`, `size` e `sort`.

3. **Busca**: O endpoint `/api/v1/products/search` não retorna paginação, apenas lista de produtos filtrados.

4. **Pedidos**: O endpoint `/api/v1/orders` retorna apenas lista simples (sem paginação explícita).

5. **Relatórios**: Todos os endpoints de `/api/v1/reports` requerem role ADMIN e suportam filtros opcionais de data (`startDate` e `endDate`).

6. **Circuit Breaker**: Quando Elasticsearch falha, as buscas automaticamente usam MySQL como fallback (sem fuzzy matching).

---

**Última atualização**: Outubro 2025
