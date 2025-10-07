-- Failed Events Table for DLQ Automatic Reprocessing
-- Stores events that failed processing for automatic retry with exponential backoff

CREATE TABLE IF NOT EXISTS failed_events (
    id CHAR(36) PRIMARY KEY,
    original_topic VARCHAR(100) NOT NULL,
    event_key VARCHAR(255),
    event_payload TEXT NOT NULL,
    exception_message TEXT,
    stack_trace TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 10,
    next_retry_at TIMESTAMP NULL,
    last_retry_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    processing_notes TEXT,

    -- Indexes for efficient querying
    INDEX idx_failed_events_status (status),
    INDEX idx_failed_events_topic_status (original_topic, status),
    INDEX idx_failed_events_next_retry (next_retry_at),
    INDEX idx_failed_events_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add CHECK constraint for status values (MySQL 8.0.16+)
ALTER TABLE failed_events
ADD CONSTRAINT chk_failed_event_status
CHECK (status IN ('PENDING', 'RETRYING', 'PROCESSED', 'FAILED', 'MAX_RETRIES_REACHED'));

-- Comment on table
ALTER TABLE failed_events COMMENT = 'Stores failed Kafka events for automatic reprocessing with exponential backoff';
