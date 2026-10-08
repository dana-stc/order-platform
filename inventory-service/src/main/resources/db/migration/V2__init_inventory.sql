CREATE TABLE product (
    sku       VARCHAR(64) PRIMARY KEY,
    available INT         NOT NULL CHECK (available >= 0)
);

CREATE TABLE reservation (
    id       UUID        PRIMARY KEY,
    order_id UUID        NOT NULL,
    sku      VARCHAR(64) NOT NULL REFERENCES product (sku),
    quantity INT         NOT NULL,
    status   VARCHAR(16) NOT NULL
);

CREATE INDEX idx_reservation_order ON reservation (order_id, status);

-- Demo catalogue. SKU-RARE has a single unit to demonstrate the "out of stock" path.
INSERT INTO product (sku, available) VALUES
    ('SKU-LAPTOP', 500),
    ('SKU-PHONE', 10000),
    ('SKU-MOUSE', 1000000),
    ('SKU-RARE', 1);
