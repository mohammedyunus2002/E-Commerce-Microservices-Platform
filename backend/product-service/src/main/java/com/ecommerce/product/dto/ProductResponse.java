package com.ecommerce.product.dto;

import com.ecommerce.product.domain.Product;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        String imageUrl,
        boolean active,
        CategorySummary category,
        Instant createdAt,
        Instant updatedAt) {

    public record CategorySummary(Long id, String name, String slug) {
    }

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getImageUrl(),
                product.isActive(),
                new CategorySummary(
                        product.getCategory().getId(),
                        product.getCategory().getName(),
                        product.getCategory().getSlug()),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
