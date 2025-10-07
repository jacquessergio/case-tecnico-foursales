-- V2__add_performance_indexes.sql
-- Performance optimization indexes
-- Prevents full table scans on frequently queried columns

-- ============================================================
-- ORDERS TABLE INDEXES
-- ============================================================

-- Composite index for user orders by status
-- Optimizes: SELECT * FROM orders WHERE user_id = ? AND status = ?
CREATE INDEX idx_orders_user_id_status ON orders(user_id, status);

-- Index for recent orders queries
-- Optimizes: SELECT * FROM orders ORDER BY data_criacao DESC
CREATE INDEX idx_orders_created_at ON orders(data_criacao DESC);

-- Composite index for admin order filtering
-- Optimizes: SELECT * FROM orders WHERE status = ? ORDER BY data_criacao DESC
CREATE INDEX idx_orders_status_created ON orders(status, data_criacao DESC);

-- ============================================================
-- ORDER_ITEMS TABLE INDEXES
-- ============================================================

-- Foreign key index for order items lookup
-- Optimizes: SELECT * FROM order_items WHERE order_id = ?
CREATE INDEX idx_order_items_order_id ON order_items(order_id);

-- Foreign key index for product sales tracking
-- Optimizes: SELECT * FROM order_items WHERE product_id = ?
CREATE INDEX idx_order_items_product_id ON order_items(product_id);

-- ============================================================
-- PRODUCTS TABLE INDEXES
-- ============================================================

-- Composite index for category + stock filtering
-- Optimizes: SELECT * FROM products WHERE categoria = ? AND quantidade_estoque > 0
CREATE INDEX idx_products_categoria_stock ON products(categoria, quantidade_estoque);

-- Index for recent products
-- Optimizes: SELECT * FROM products ORDER BY data_criacao DESC
CREATE INDEX idx_products_created_at ON products(data_criacao DESC);

-- Text search index for product names (MySQL FULLTEXT not used due to Elasticsearch)
-- Optimizes: SELECT * FROM products WHERE nome LIKE '%search%'
CREATE INDEX idx_products_nome ON products(nome);

-- ============================================================
-- OUTBOX_EVENTS TABLE INDEXES (CRITICAL FOR PERFORMANCE)
-- ============================================================

-- CRITICAL: Composite index for unpublished events query
-- Optimizes: SELECT * FROM outbox_events WHERE published = false ORDER BY created_at
-- OutboxPublisher runs every 5 seconds - this index prevents full table scan
CREATE INDEX idx_outbox_published_created ON outbox_events(published, created_at);

-- Index for event lookup by aggregate
-- Optimizes: SELECT * FROM outbox_events WHERE aggregate_type = ? AND aggregate_id = ?
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_type, aggregate_id);

-- Index for failed events monitoring
-- Optimizes: SELECT * FROM outbox_events WHERE published = false AND retry_count > ?
CREATE INDEX idx_outbox_retry_failed ON outbox_events(published, retry_count);

-- ============================================================
-- ANALYZE TABLES (Update Statistics)
-- ============================================================

-- Update table statistics for query optimizer
ANALYZE TABLE orders, order_items, products, outbox_events;
