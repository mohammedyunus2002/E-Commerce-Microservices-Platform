CREATE TABLE payments (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    order_id     BIGINT         NOT NULL,
    user_id      BIGINT         NOT NULL,
    amount       DECIMAL(12, 2) NOT NULL,
    currency     VARCHAR(3)     NOT NULL,
    status       VARCHAR(16)    NOT NULL,
    failure_code VARCHAR(64),
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6)    NOT NULL,
    version      BIGINT         NOT NULL,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uk_payments_order UNIQUE (order_id),
    CONSTRAINT ck_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_payments_currency CHECK (currency = 'USD')
);

CREATE INDEX idx_payments_user_created ON payments (user_id, created_at);
