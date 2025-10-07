-- V4__add_processed_events_indexes.sql
-- Performance optimization indexes for consumer database

-- ============================================================
-- PROCESSED_EVENTS TABLE INDEXES (CRITICAL FOR IDEMPOTENCY)
-- ============================================================

-- CRITICAL: Unique index for event ID lookup (idempotency check)
-- Optimizes: SELECT * FROM processed_events WHERE event_id = ?
-- This query runs for EVERY Kafka message received
CREATE INDEX idx_processed_event_id ON processed_events(event_id);

-- Composite index for aggregate event history
-- Optimizes: SELECT * FROM processed_events WHERE aggregate_id = ? ORDER BY processed_at DESC
CREATE INDEX idx_processed_aggregate_processed ON processed_events(aggregate_id, processed_at DESC);

-- Composite index for event status monitoring
-- Optimizes: SELECT * FROM processed_events WHERE status = ? AND event_type = ?
CREATE INDEX idx_processed_status_type ON processed_events(status, event_type);

-- Update table statistics for query optimizer
ANALYZE TABLE processed_events;
