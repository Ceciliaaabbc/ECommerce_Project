CREATE TABLE IF NOT EXISTS product_variants (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku VARCHAR(255),
    option_name VARCHAR(255),
    option_value VARCHAR(255),
    price NUMERIC(19, 2),
    stock INTEGER,
    active BOOLEAN DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS ix_product_variants_product_id ON product_variants (product_id);
CREATE UNIQUE INDEX IF NOT EXISTS ux_product_variants_sku ON product_variants (sku) WHERE sku IS NOT NULL AND sku <> '';

CREATE TABLE IF NOT EXISTS product_images (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    image_url TEXT,
    sort_order INTEGER DEFAULT 0,
    primary_image BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS ix_product_images_product_id ON product_images (product_id);

INSERT INTO product_images (product_id, image_url, sort_order, primary_image)
SELECT id, image_url, 0, TRUE
FROM products
WHERE image_url IS NOT NULL
  AND image_url <> ''
  AND NOT EXISTS (
      SELECT 1 FROM product_images WHERE product_images.product_id = products.id
  );

ALTER TABLE cart_items
ADD COLUMN IF NOT EXISTS variant_id BIGINT,
ADD COLUMN IF NOT EXISTS sku VARCHAR(255),
ADD COLUMN IF NOT EXISTS variant_name VARCHAR(255);

DROP INDEX IF EXISTS ix_cart_items_user_product;
CREATE INDEX IF NOT EXISTS ix_cart_items_user_product_variant ON cart_items (user_email, product_id, variant_id);
