package com.ecommerce.product.dto;

import java.math.BigDecimal;

/**
 * @param query      free text matched against name, description, and SKU
 * @param activeOnly true for shopper-facing searches; admins can include inactive products
 */
public record ProductSearchCriteria(
        String query,
        Long categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean activeOnly) {
}
