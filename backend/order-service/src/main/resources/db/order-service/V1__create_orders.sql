CREATE TABLE carts (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_carts PRIMARY KEY (id),
    CONSTRAINT uk_carts_user UNIQUE (user_id)
);

CREATE TABLE cart_items (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    cart_id    BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity   INT    NOT NULL,
    CONSTRAINT pk_cart_items PRIMARY KEY (id),
    CONSTRAINT uk_cart_items_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0 AND quantity <= 100)
);

-- customer_orders avoids the reserved word ORDER. order-service is the only writer of status.
CREATE TABLE customer_orders (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    user_id      BIGINT         NOT NULL,
    status       VARCHAR(32)    NOT NULL,
    currency     VARCHAR(3)     NOT NULL,
    total_amount DECIMAL(12, 2) NOT NULL,
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6)    NOT NULL,
    version      BIGINT         NOT NULL,
    CONSTRAINT pk_customer_orders PRIMARY KEY (id),
    CONSTRAINT ck_customer_orders_total_positive CHECK (total_amount > 0),
    CONSTRAINT ck_customer_orders_currency CHECK (currency = 'USD')
);

CREATE INDEX idx_customer_orders_user_created ON customer_orders (user_id, created_at);

CREATE TABLE order_items (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    order_id     BIGINT         NOT NULL,
    product_id   BIGINT         NOT NULL,
    sku          VARCHAR(40)    NOT NULL,
    product_name VARCHAR(200)   NOT NULL,
    unit_price   DECIMAL(12, 2) NOT NULL,
    quantity     INT            NOT NULL,
    line_total   DECIMAL(12, 2) NOT NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT uk_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES customer_orders (id) ON DELETE CASCADE,
    CONSTRAINT ck_order_items_price CHECK (unit_price > 0),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0 AND quantity <= 100),
    CONSTRAINT ck_order_items_line_total CHECK (line_total > 0)
);

CREATE TABLE order_status_changes (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    BIGINT       NOT NULL,
    from_status VARCHAR(32),
    to_status   VARCHAR(32)  NOT NULL,
    reason      VARCHAR(200),
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_order_status_changes PRIMARY KEY (id),
    CONSTRAINT fk_order_status_changes_order FOREIGN KEY (order_id) REFERENCES customer_orders (id) ON DELETE CASCADE
);

CREATE INDEX idx_order_status_changes_order ON order_status_changes (order_id, created_at);
