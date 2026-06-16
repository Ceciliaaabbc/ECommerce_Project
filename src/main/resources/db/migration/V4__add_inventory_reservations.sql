ALTER TABLE products
ADD COLUMN IF NOT EXISTS reserved_stock INTEGER DEFAULT 0;

UPDATE products
SET reserved_stock = 0
WHERE reserved_stock IS NULL;

ALTER TABLE orders
ADD COLUMN IF NOT EXISTS inventory_reserved BOOLEAN DEFAULT FALSE;

UPDATE orders
SET inventory_reserved = FALSE
WHERE inventory_reserved IS NULL;
