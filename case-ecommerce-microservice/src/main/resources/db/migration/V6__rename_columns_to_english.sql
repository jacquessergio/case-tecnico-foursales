-- Migration: Rename Portuguese columns to English
-- This migration renames database columns to use English naming convention

-- Step 1: Drop check constraints that reference Portuguese column names
ALTER TABLE products DROP CHECK products_chk_1;
ALTER TABLE products DROP CHECK products_chk_2;

-- Step 2: Rename products table columns one by one
ALTER TABLE products CHANGE COLUMN nome name VARCHAR(255) NOT NULL;
ALTER TABLE products CHANGE COLUMN descricao description TEXT;
ALTER TABLE products CHANGE COLUMN preco price DECIMAL(10,2) NOT NULL;
ALTER TABLE products CHANGE COLUMN categoria category VARCHAR(100) NOT NULL;
ALTER TABLE products CHANGE COLUMN quantidade_estoque stock_quantity INT NOT NULL DEFAULT 0;
ALTER TABLE products CHANGE COLUMN data_criacao created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE products CHANGE COLUMN data_atualizacao updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- Step 3: Recreate check constraints with English column names
ALTER TABLE products ADD CONSTRAINT products_chk_price CHECK (price >= 0);
ALTER TABLE products ADD CONSTRAINT products_chk_stock CHECK (stock_quantity >= 0);
