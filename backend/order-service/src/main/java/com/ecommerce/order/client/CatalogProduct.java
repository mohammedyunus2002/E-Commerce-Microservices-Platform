package com.ecommerce.order.client;

import java.math.BigDecimal;

/** Fields read from product-service. Extra JSON properties are ignored. */
public record CatalogProduct(Long id, String sku, String name, BigDecimal price, boolean active) {
}
