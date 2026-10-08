CREATE TABLE notification (
    id          UUID         PRIMARY KEY,
    order_id    UUID         NOT NULL,
    customer_id VARCHAR(64),
    type        VARCHAR(32)  NOT NULL,
    body        TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_notification_order ON notification (order_id);
