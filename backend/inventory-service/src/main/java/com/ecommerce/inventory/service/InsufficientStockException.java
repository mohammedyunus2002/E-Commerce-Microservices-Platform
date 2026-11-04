package com.ecommerce.inventory.service;

import com.ecommerce.common.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a product cannot supply the requested units. It is a runtime exception, so any
 * surrounding transaction rolls back and lines reserved earlier in the same order are undone.
 */
public class InsufficientStockException extends ApiException {

    private final long productId;
    private final int requested;
    private final int available;

    public InsufficientStockException(long productId, int requested, int available) {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                "Product " + productId + " has " + available + " units available, " + requested + " requested");
        this.productId = productId;
        this.requested = requested;
        this.available = available;
    }

    public long getProductId() {
        return productId;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
