CREATE TABLE outbox_events (
    id             VARCHAR(36)   NOT NULL,
    topic          VARCHAR(64)   NOT NULL,
    aggregate_key  VARCHAR(64)   NOT NULL,
    payload        VARCHAR(4000) NOT NULL,
    causation_id   VARCHAR(36),
    created_at     DATETIME(6)   NOT NULL,
    published_at   DATETIME(6),
    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_events_unpublished ON outbox_events (published_at, created_at);

CREATE TABLE processed_events (
    event_id     VARCHAR(36) NOT NULL,
    topic        VARCHAR(64) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id)
);

-- A cancel that arrives before order-created is stored here so the late event does not reserve stock.
CREATE TABLE order_tombstones (
    order_id   BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_order_tombstones PRIMARY KEY (order_id)
);

-- One row per order, locked so reserve and cancel for that order cannot interleave.
CREATE TABLE order_gates (
    order_id BIGINT NOT NULL,
    CONSTRAINT pk_order_gates PRIMARY KEY (order_id)
);
