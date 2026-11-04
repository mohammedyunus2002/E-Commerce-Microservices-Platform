package com.ecommerce.inventory.dto;

import com.ecommerce.inventory.domain.InventoryItem;

import java.time.Instant;

public record InventoryItemResponse(
        Long productId,
        int availableQuantity,
        int reservedQuantity,
        Instant updatedAt) {

    public static InventoryItemResponse from(InventoryItem item) {
        return new InventoryItemResponse(item.getProductId(), item.getAvailableQuantity(),
                item.getReservedQuantity(), item.getUpdatedAt());
    }
}
