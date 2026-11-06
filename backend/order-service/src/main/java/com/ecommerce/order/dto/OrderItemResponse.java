package com.ecommerce.order.dto;

import com.ecommerce.order.domain.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long productId,
        String sku,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getProductId(), item.getSku(), item.getProductName(),
                item.getUnitPrice(), item.getQuantity(), item.getLineTotal());
    }
}
