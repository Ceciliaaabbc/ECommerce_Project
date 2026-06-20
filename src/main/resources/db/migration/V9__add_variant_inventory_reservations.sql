ALTER TABLE product_variants
ADD COLUMN IF NOT EXISTS reserved_stock INTEGER DEFAULT 0;

UPDATE product_variants
SET reserved_stock = 0
WHERE reserved_stock IS NULL;

ALTER TABLE order_items
ADD COLUMN IF NOT EXISTS variant_id BIGINT,
ADD COLUMN IF NOT EXISTS sku VARCHAR(255),
ADD COLUMN IF NOT EXISTS variant_name VARCHAR(255);

CREATE INDEX IF NOT EXISTS ix_order_items_variant_id ON order_items (variant_id);
