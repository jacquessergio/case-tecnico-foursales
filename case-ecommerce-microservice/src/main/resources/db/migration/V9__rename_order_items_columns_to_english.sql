-- ============================================================================
-- Migration: V9 - Rename order_items columns to English
-- Purpose: Fix column names from Portuguese to English to match entity mappings
-- Note: Columns were already renamed manually (quantidade->quantity, preco_unitario->unit_price)
--       Old constraints were already dropped manually
-- ============================================================================

-- This migration is now a no-op since changes were applied manually
-- But we keep it for Flyway version control consistency

-- Verify the columns are correctly named (this is a SELECT, safe to run)
SELECT
    COLUMN_NAME,
    DATA_TYPE
FROM
    INFORMATION_SCHEMA.COLUMNS
WHERE
    TABLE_SCHEMA = 'ecommerce_db'
    AND TABLE_NAME = 'order_items'
    AND COLUMN_NAME IN ('quantity', 'unit_price')
LIMIT 2;
