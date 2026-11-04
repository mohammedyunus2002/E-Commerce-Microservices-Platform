package com.ecommerce.product.web;

import com.ecommerce.common.error.ApiException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * Parses {@code sort=field[,asc|desc]} against a whitelist, so clients cannot sort on
 * arbitrary or unindexed columns. Product ID is always the tiebreaker for stable paging.
 */
final class ProductSort {

    private static final Map<String, String> ALLOWED = Map.of(
            "name", "name",
            "price", "price",
            "createdat", "createdAt",
            "newest", "createdAt");

    private ProductSort() {
    }

    static Sort parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        }
        String[] parts = raw.split(",");
        String key = parts[0].trim().toLowerCase(Locale.ROOT);
        String property = ALLOWED.get(key);
        if (property == null || parts.length > 2) {
            throw invalid(raw);
        }

        Sort.Direction direction = key.equals("newest") ? Sort.Direction.DESC : Sort.Direction.ASC;
        if (parts.length == 2) {
            direction = Sort.Direction.fromOptionalString(parts[1].trim())
                    .orElseThrow(() -> invalid(raw));
        }
        return Sort.by(new Sort.Order(direction, property), Sort.Order.asc("id"));
    }

    private static ApiException invalid(String raw) {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT",
                "Unsupported sort '" + raw + "'. Use name, price, or createdAt with optional ,asc or ,desc");
    }
}
