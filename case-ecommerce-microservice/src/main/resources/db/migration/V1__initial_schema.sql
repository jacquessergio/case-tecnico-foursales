-- Flyway Migration V1: Initial Schema
-- Creates all base tables for the e-commerce system

-- ============================================================================
-- TABLE: users
-- Stores user accounts with authentication and authorization data
-- ============================================================================
CREATE TABLE users (
    id BINARY(16) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_email (email),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='User accounts with authentication data';

-- ============================================================================
-- TABLE: products
-- Product catalog with pricing and inventory
-- ============================================================================
CREATE TABLE products (
    id BINARY(16) PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    descricao TEXT,
    preco DECIMAL(10,2) NOT NULL,
    categoria VARCHAR(100) NOT NULL,
    quantidade_estoque INT NOT NULL DEFAULT 0,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_categoria (categoria),
    INDEX idx_quantidade_estoque (quantidade_estoque),
    INDEX idx_data_criacao (data_criacao),
    CHECK (preco >= 0),
    CHECK (quantidade_estoque >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Product catalog with pricing and inventory';

-- ============================================================================
-- TABLE: orders
-- Customer orders with status tracking
-- ============================================================================
CREATE TABLE orders (
    id BINARY(16) PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    status VARCHAR(50) NOT NULL,
    valor_total DECIMAL(10,2) NOT NULL,
    data_criacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    data_pagamento TIMESTAMP NULL,
    idempotency_key VARCHAR(100),
    stock_updated BOOLEAN NOT NULL DEFAULT FALSE,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_user_id (user_id),
    INDEX idx_status (status),
    INDEX idx_data_criacao (data_criacao),
    INDEX idx_stock_updated (stock_updated),
    UNIQUE INDEX idx_user_idempotency (user_id, idempotency_key),
    CHECK (valor_total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Customer orders with status tracking';

-- ============================================================================
-- TABLE: order_items
-- Line items for each order
-- ============================================================================
CREATE TABLE order_items (
    id BINARY(16) PRIMARY KEY,
    order_id BINARY(16) NOT NULL,
    product_id BINARY(16) NOT NULL,
    quantidade INT NOT NULL,
    preco_unitario DECIMAL(10,2) NOT NULL,

    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id),
    CHECK (quantidade > 0),
    CHECK (preco_unitario >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Line items for each order';

-- ============================================================================
-- TABLE: outbox_events (Transactional Outbox Pattern)
-- Ensures reliable event publishing to Kafka
-- ============================================================================
CREATE TABLE outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    topic VARCHAR(100) NOT NULL,
    partition_key VARCHAR(100),
    published BOOLEAN NOT NULL DEFAULT FALSE,
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_retry_at TIMESTAMP NULL,
    published_at TIMESTAMP NULL,

    INDEX idx_published_created (published, created_at),
    INDEX idx_aggregate (aggregate_type, aggregate_id),
    INDEX idx_retry_count (retry_count),
    CHECK (retry_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Transactional outbox for reliable event publishing';
