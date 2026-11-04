package com.ecommerce.inventory.dto;

/** Public stock view. Products without a stock record are reported as 0 available. */
public record AvailabilityResponse(Long productId, int availableQuantity, boolean inStock) {

    public static AvailabilityResponse of(long productId, int availableQuantity) {
        return new AvailabilityResponse(productId, availableQuantity, availableQuantity > 0);
    }
}
