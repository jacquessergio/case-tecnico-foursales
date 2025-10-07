-- V5__add_updated_at_to_users.sql
-- Add missing updated_at column to users table

ALTER TABLE users
ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
AFTER created_at;

-- Create index for audit queries
CREATE INDEX idx_users_updated_at ON users(updated_at);
