CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255),
    email VARCHAR(255),
    password VARCHAR(255),
    role VARCHAR(50) DEFAULT 'USER'
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email ON users (email);

CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255),
    description TEXT,
    price NUMERIC(19, 2),
    image_url TEXT,
    stock INTEGER,
    category VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS ix_products_category ON products (category);
CREATE INDEX IF NOT EXISTS ix_products_title ON products (title);

CREATE TABLE IF NOT EXISTS cart_items (
    id BIGSERIAL PRIMARY KEY,
    user_email VARCHAR(255),
    product_id BIGINT,
    title VARCHAR(255),
    price NUMERIC(19, 2),
    quantity INTEGER
);

CREATE INDEX IF NOT EXISTS ix_cart_items_user_email ON cart_items (user_email);
CREATE INDEX IF NOT EXISTS ix_cart_items_user_product ON cart_items (user_email, product_id);

CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    user_email VARCHAR(255),
    total NUMERIC(19, 2),
    status VARCHAR(50),
    created_at TIMESTAMP,
    payment_status VARCHAR(50),
    stripe_session_id VARCHAR(255),
    shipping_address_id BIGINT
);

CREATE INDEX IF NOT EXISTS ix_orders_user_email ON orders (user_email);
CREATE INDEX IF NOT EXISTS ix_orders_stripe_session_id ON orders (stripe_session_id);

CREATE TABLE IF NOT EXISTS order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT,
    product_id BIGINT,
    title VARCHAR(255),
    price NUMERIC(19, 2),
    quantity INTEGER
);

CREATE INDEX IF NOT EXISTS ix_order_items_order_id ON order_items (order_id);

CREATE TABLE IF NOT EXISTS reviews (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT,
    user_email VARCHAR(255),
    rating INTEGER,
    comment TEXT,
    created_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_reviews_product_id ON reviews (product_id);

CREATE TABLE IF NOT EXISTS addresses (
    id BIGSERIAL PRIMARY KEY,
    user_email VARCHAR(255),
    recipient_name VARCHAR(255),
    phone VARCHAR(255),
    country VARCHAR(255),
    province VARCHAR(255),
    city VARCHAR(255),
    street TEXT,
    postal_code VARCHAR(255),
    default_address BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS ix_addresses_user_email ON addresses (user_email);
