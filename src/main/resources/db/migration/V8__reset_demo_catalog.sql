TRUNCATE TABLE
    cart_items,
    order_items,
    orders,
    reviews,
    product_images,
    product_variants,
    products
RESTART IDENTITY;

WITH inserted_products AS (
    INSERT INTO products (title, description, price, image_url, stock, reserved_stock, category)
    VALUES
        (
            'Aurora Wireless Headphones',
            'Premium over-ear wireless headphones with soft cushions, active noise reduction, and long battery life for travel, study, and work.',
            129.00,
            'https://ecommerce-frontend-one-theta.vercel.app/products/aurora-wireless-headphones.png',
            36,
            0,
            'Electronics'
        ),
        (
            'Terra Smart Watch',
            'A lightweight round-face smart watch with fitness tracking, message alerts, and an all-day comfort strap.',
            189.00,
            'https://ecommerce-frontend-one-theta.vercel.app/products/terra-smart-watch.png',
            24,
            0,
            'Electronics'
        ),
        (
            'Luma Desk Lamp',
            'Minimal adjustable LED desk lamp with warm light modes for reading, study, and focused workspace setups.',
            64.00,
            'https://ecommerce-frontend-one-theta.vercel.app/products/luma-desk-lamp.png',
            18,
            0,
            'Home'
        ),
        (
            'Nova Ceramic Mug',
            'Hand-finished ceramic mug with a speckled glaze, comfortable handle, and a calm everyday coffee ritual feel.',
            28.00,
            'https://ecommerce-frontend-one-theta.vercel.app/products/nova-ceramic-mug.png',
            42,
            0,
            'Lifestyle'
        ),
        (
            'CloudStep Running Shoes',
            'Breathable running shoes with a cushioned sole, flexible upper, and balanced support for daily training.',
            98.00,
            'https://ecommerce-frontend-one-theta.vercel.app/products/cloudstep-running-shoes.png',
            30,
            0,
            'Fashion'
        ),
        (
            'Drift Everyday Backpack',
            'Compact everyday backpack with padded straps, laptop space, water-resistant fabric, and clean commuter styling.',
            76.00,
            'https://ecommerce-frontend-one-theta.vercel.app/products/drift-everyday-backpack.png',
            16,
            0,
            'Bags'
        )
    RETURNING id, title, price, stock, image_url
)
INSERT INTO product_images (product_id, image_url, sort_order, primary_image)
SELECT id, image_url, 0, TRUE
FROM inserted_products;

INSERT INTO product_variants (product_id, sku, option_name, option_value, price, stock, active)
SELECT id, 'AURORA-IVORY', 'Color', 'Ivory', price, 18, TRUE FROM products WHERE title = 'Aurora Wireless Headphones'
UNION ALL
SELECT id, 'AURORA-GRAPHITE', 'Color', 'Graphite', price + 10, 18, TRUE FROM products WHERE title = 'Aurora Wireless Headphones'
UNION ALL
SELECT id, 'TERRA-OLIVE', 'Strap', 'Olive Silicone', price, 12, TRUE FROM products WHERE title = 'Terra Smart Watch'
UNION ALL
SELECT id, 'TERRA-BLACK', 'Strap', 'Black Silicone', price, 12, TRUE FROM products WHERE title = 'Terra Smart Watch'
UNION ALL
SELECT id, 'LUMA-WHITE', 'Finish', 'Matte White', price, 9, TRUE FROM products WHERE title = 'Luma Desk Lamp'
UNION ALL
SELECT id, 'LUMA-BRASS', 'Finish', 'White + Brass', price + 6, 9, TRUE FROM products WHERE title = 'Luma Desk Lamp'
UNION ALL
SELECT id, 'NOVA-BLUE-RIM', 'Glaze', 'Blue Rim', price, 21, TRUE FROM products WHERE title = 'Nova Ceramic Mug'
UNION ALL
SELECT id, 'NOVA-SPECKLE', 'Glaze', 'Warm Speckle', price, 21, TRUE FROM products WHERE title = 'Nova Ceramic Mug'
UNION ALL
SELECT id, 'CLOUDSTEP-8', 'Size', 'US 8', price, 10, TRUE FROM products WHERE title = 'CloudStep Running Shoes'
UNION ALL
SELECT id, 'CLOUDSTEP-9', 'Size', 'US 9', price, 10, TRUE FROM products WHERE title = 'CloudStep Running Shoes'
UNION ALL
SELECT id, 'CLOUDSTEP-10', 'Size', 'US 10', price, 10, TRUE FROM products WHERE title = 'CloudStep Running Shoes'
UNION ALL
SELECT id, 'DRIFT-NAVY', 'Color', 'Navy', price, 8, TRUE FROM products WHERE title = 'Drift Everyday Backpack'
UNION ALL
SELECT id, 'DRIFT-CHARCOAL', 'Color', 'Charcoal', price, 8, TRUE FROM products WHERE title = 'Drift Everyday Backpack';
