CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  role VARCHAR(20) NOT NULL CHECK (role IN ('CUSTOMER','OWNER','CASHIER')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE pizzas (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  base_price NUMERIC(10,2) NOT NULL,
  category VARCHAR(50) NOT NULL,
  image_url VARCHAR(500) NOT NULL DEFAULT '/placeholder.svg',
  active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE pizza_sizes (id BIGSERIAL PRIMARY KEY, name VARCHAR(50) NOT NULL, price_delta NUMERIC(10,2) NOT NULL DEFAULT 0);
CREATE TABLE crusts      (id BIGSERIAL PRIMARY KEY, name VARCHAR(50) NOT NULL, price_delta NUMERIC(10,2) NOT NULL DEFAULT 0);
CREATE TABLE toppings    (id BIGSERIAL PRIMARY KEY, name VARCHAR(50) NOT NULL, price_delta NUMERIC(10,2) NOT NULL DEFAULT 0);

CREATE SEQUENCE order_number_seq START WITH 1001;

CREATE TABLE orders (
  id UUID PRIMARY KEY,
  order_number BIGINT NOT NULL UNIQUE,
  user_id BIGINT REFERENCES users(id),
  status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','NEW','PREPARING','READY','COMPLETED','CANCELLED')),
  payment_status VARCHAR(20) NOT NULL CHECK (payment_status IN ('UNPAID','PAID','FAILED')),
  type VARCHAR(20) NOT NULL CHECK (type IN ('PICKUP','DELIVERY')),
  customer_name VARCHAR(100) NOT NULL,
  customer_email VARCHAR(255) NOT NULL,
  customer_phone VARCHAR(50) NOT NULL,
  subtotal NUMERIC(10,2) NOT NULL,
  service_fee NUMERIC(10,2) NOT NULL,
  total NUMERIC(10,2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  paid_at TIMESTAMPTZ
);
CREATE INDEX orders_status_idx ON orders(status);
CREATE INDEX orders_user_idx ON orders(user_id);

CREATE TABLE order_items (
  id BIGSERIAL PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  pizza_name VARCHAR(100) NOT NULL,
  size VARCHAR(50) NOT NULL,
  crust VARCHAR(50) NOT NULL,
  toppings JSONB NOT NULL DEFAULT '[]',
  quantity INT NOT NULL CHECK (quantity >= 1),
  line_price NUMERIC(10,2) NOT NULL
);

CREATE TABLE payments (
  id BIGSERIAL PRIMARY KEY,
  order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  gateway_bill_id VARCHAR(100),
  amount NUMERIC(10,2) NOT NULL,
  status VARCHAR(20) NOT NULL,
  raw_callback JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
