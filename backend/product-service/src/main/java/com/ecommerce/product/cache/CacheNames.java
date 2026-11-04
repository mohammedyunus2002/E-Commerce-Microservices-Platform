package com.ecommerce.product.cache;

public final class CacheNames {

    /** Product detail by ID. Evicted on update and deactivation. */
    public static final String PRODUCTS = "products";

    /** The full category list under a single key. Evicted on any category change. */
    public static final String CATEGORIES = "categories";

    private CacheNames() {
    }
}
