CREATE TABLE payment (
    id             UUID          PRIMARY KEY,
    order_id       UUID          NOT NULL UNIQUE,
    amount         NUMERIC(12,2) NOT NULL,
    status         VARCHAR(16)   NOT NULL,
    failure_reason VARCHAR(255),
    created_at     TIMESTAMPTZ   NOT NULL
);
