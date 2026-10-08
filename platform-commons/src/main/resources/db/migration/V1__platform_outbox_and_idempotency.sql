-- Shared by every service (each one owns its own database).

CREATE TABLE outbox (
    id           BIGSERIAL PRIMARY KEY,
    aggregate_id VARCHAR(64)  NOT NULL,
    topic        VARCHAR(128) NOT NULL,
    message_type VARCHAR(64)  NOT NULL,
    payload      TEXT         NOT NULL,
    trace_id     VARCHAR(64),
    span_id      VARCHAR(32),
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);

-- Partial index: the poller only ever looks at unpublished rows, so the index stays tiny.
CREATE INDEX idx_outbox_pending ON outbox (id) WHERE published_at IS NULL;

CREATE TABLE processed_message (
    message_id   UUID         NOT NULL,
    consumer     VARCHAR(64)  NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (message_id, consumer)
);
