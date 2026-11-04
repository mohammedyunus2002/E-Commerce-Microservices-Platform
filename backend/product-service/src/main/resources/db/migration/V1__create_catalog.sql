CREATE TABLE categories (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100)  NOT NULL,
    slug        VARCHAR(120)  NOT NULL,
    description VARCHAR(500),
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name),
    CONSTRAINT uk_categories_slug UNIQUE (slug)
);

CREATE TABLE products (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    sku         VARCHAR(40)    NOT NULL,
    name        VARCHAR(200)   NOT NULL,
    description VARCHAR(4000),
    price       DECIMAL(12, 2) NOT NULL,
    image_url   VARCHAR(500),
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    category_id BIGINT         NOT NULL,
    created_at  DATETIME(6)    NOT NULL,
    updated_at  DATETIME(6)    NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_products_price_positive CHECK (price > 0)
);

-- Public listing filters on active products by category and sorts by price or recency.
CREATE INDEX idx_products_active_category_price ON products (active, category_id, price);
CREATE INDEX idx_products_active_price ON products (active, price);
CREATE INDEX idx_products_active_created ON products (active, created_at);
