package com.ecommerce.product.repository;

import com.ecommerce.product.domain.Product;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Composable filters for product search. Each returns {@code null} when its input is absent,
 * which {@link Specification#allOf} treats as "no restriction".
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> activeOnly(boolean activeOnly) {
        return activeOnly ? (root, query, cb) -> cb.isTrue(root.get("active")) : null;
    }

    public static Specification<Product> textMatches(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, '\\'),
                cb.like(cb.lower(root.get("description")), pattern, '\\'),
                cb.like(cb.lower(root.get("sku")), pattern, '\\'));
    }

    public static Specification<Product> inCategory(Long categoryId) {
        return categoryId == null ? null : (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> priceAtLeast(BigDecimal min) {
        return min == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<Product> priceAtMost(BigDecimal max) {
        return max == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    /** Treats user input literally so {@code %} and {@code _} do not act as wildcards. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
