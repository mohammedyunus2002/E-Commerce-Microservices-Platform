-- product_id is owned by product-service. It is not a foreign key across databases;
-- inventory-service checks the product exists when a stock record is first created.
CREATE TABLE inventory_items (
    product_id         BIGINT      NOT NULL,
    available_quantity INT         NOT NULL,
    reserved_quantity  INT         NOT NULL DEFAULT 0,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    CONSTRAINT pk_inventory_items PRIMARY KEY (product_id),
    CONSTRAINT ck_inventory_available_non_negative CHECK (available_quantity >= 0),
    CONSTRAINT ck_inventory_reserved_non_negative CHECK (reserved_quantity >= 0)
);

CREATE INDEX idx_inventory_items_available ON inventory_items (available_quantity);

-- One row per order line. The unique key makes a duplicate reservation for the same order
-- fail and roll back, which keeps reservation idempotent under concurrent redelivery.
CREATE TABLE stock_reservations (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    order_id   BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    status     VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_stock_reservations PRIMARY KEY (id),
    CONSTRAINT uk_stock_reservations_order_product UNIQUE (order_id, product_id),
    CONSTRAINT fk_stock_reservations_item FOREIGN KEY (product_id) REFERENCES inventory_items (product_id),
    CONSTRAINT ck_stock_reservations_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_stock_reservations_status ON stock_reservations (status);
