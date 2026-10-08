CREATE TABLE orders (
    id              UUID          PRIMARY KEY,
    customer_id     VARCHAR(64)   NOT NULL,
    status          VARCHAR(32)   NOT NULL,
    total_amount    NUMERIC(12,2) NOT NULL,
    idempotency_key VARCHAR(128)  UNIQUE,
    failure_reason  VARCHAR(255),
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL
);

CREATE TABLE order_line (
    order_id   UUID          NOT NULL REFERENCES orders (id),
    sku        VARCHAR(64)   NOT NULL,
    quantity   INT           NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_order_line_order ON order_line (order_id);
