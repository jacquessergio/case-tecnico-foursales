-- Migration: Rename Order table Portuguese columns to English
-- This migration renames order table columns to use English naming convention

-- Step 1: Drop check constraint that references valor_total
ALTER TABLE orders DROP CHECK orders_chk_1;

-- Step 2: Rename orders table columns
ALTER TABLE orders CHANGE COLUMN valor_total total_value DECIMAL(10,2) NOT NULL;
ALTER TABLE orders CHANGE COLUMN data_criacao created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE orders CHANGE COLUMN data_atualizacao updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE orders CHANGE COLUMN data_pagamento payment_date TIMESTAMP NULL;

-- Step 3: Recreate check constraint with new column name
ALTER TABLE orders ADD CONSTRAINT orders_chk_total_value CHECK (total_value >= 0);
