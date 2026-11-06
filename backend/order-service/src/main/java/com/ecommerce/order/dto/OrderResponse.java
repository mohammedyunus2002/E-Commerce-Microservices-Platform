package com.ecommerce.order.dto;

import com.ecommerce.order.domain.CustomerOrder;
import com.ecommerce.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long userId,
        OrderStatus status,
        String currency,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        boolean cancelRequested,
        Instant createdAt,
        Instant updatedAt) {

    public static OrderResponse from(CustomerOrder order) {
        return new OrderResponse(order.getId(), order.getUserId(), order.getStatus(), order.getCurrency(),
                order.getTotalAmount(), order.getItems().stream().map(OrderItemResponse::from).toList(),
                order.isCancelRequested(), order.getCreatedAt(), order.getUpdatedAt());
    }
}
