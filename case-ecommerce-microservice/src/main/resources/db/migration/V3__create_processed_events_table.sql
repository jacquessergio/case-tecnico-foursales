-- Flyway Migration V3: Create Processed Events Table
-- Idempotency control for event processing (consumer)

-- ============================================================================
-- TABLE: processed_events
-- Tracks all processed events to prevent duplicate processing
-- ============================================================================
CREATE TABLE processed_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    error_message VARCHAR(500),

    INDEX idx_event_id (event_id),
    INDEX idx_processed_at (processed_at),
    INDEX idx_aggregate_id (aggregate_id),
    INDEX idx_status (status),
    INDEX idx_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Tracks processed events for idempotency control';
