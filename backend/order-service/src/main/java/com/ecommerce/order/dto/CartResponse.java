package com.ecommerce.order.dto;

import java.time.Instant;
import java.util.List;

public record CartResponse(Long userId, List<CartItemResponse> items, Instant updatedAt) {
}
