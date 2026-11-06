package com.ecommerce.payment.client;

import java.math.BigDecimal;

public record OrderSnapshot(Long id, Long userId, String status, String currency, BigDecimal totalAmount) {
}
