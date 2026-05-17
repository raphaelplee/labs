CREATE SCHEMA IF NOT EXISTS transflow;

-- Orders
CREATE TABLE transflow.orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id VARCHAR(255) NOT NULL UNIQUE,
    status          VARCHAR(50)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Payments
CREATE TABLE transflow.payments (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id   UUID        NOT NULL REFERENCES transflow.orders(id),
    status     VARCHAR(50) NOT NULL,
    scenario   VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Fulfillment records
CREATE TABLE transflow.fulfillment_records (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID        NOT NULL,
    subscription_id VARCHAR(255) NOT NULL,
    status          VARCHAR(50) NOT NULL,
    fulfilled_at    TIMESTAMPTZ
);

-- Index for fast lookup
CREATE INDEX idx_orders_subscription_id ON transflow.orders(subscription_id);
CREATE INDEX idx_payments_order_id ON transflow.payments(order_id);
CREATE INDEX idx_fulfillment_order_id ON transflow.fulfillment_records(order_id);
