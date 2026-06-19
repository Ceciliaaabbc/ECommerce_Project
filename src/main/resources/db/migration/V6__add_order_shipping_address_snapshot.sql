ALTER TABLE orders
ADD COLUMN IF NOT EXISTS shipping_recipient_name VARCHAR(255);

ALTER TABLE orders
ADD COLUMN IF NOT EXISTS shipping_phone VARCHAR(255);

ALTER TABLE orders
ADD COLUMN IF NOT EXISTS shipping_country VARCHAR(255);

ALTER TABLE orders
ADD COLUMN IF NOT EXISTS shipping_province VARCHAR(255);

ALTER TABLE orders
ADD COLUMN IF NOT EXISTS shipping_city VARCHAR(255);

ALTER TABLE orders
ADD COLUMN IF NOT EXISTS shipping_street TEXT;

ALTER TABLE orders
ADD COLUMN IF NOT EXISTS shipping_postal_code VARCHAR(255);

UPDATE orders o
SET shipping_recipient_name = a.recipient_name,
    shipping_phone = a.phone,
    shipping_country = a.country,
    shipping_province = a.province,
    shipping_city = a.city,
    shipping_street = a.street,
    shipping_postal_code = a.postal_code
FROM addresses a
WHERE o.shipping_address_id = a.id
  AND o.shipping_recipient_name IS NULL;
