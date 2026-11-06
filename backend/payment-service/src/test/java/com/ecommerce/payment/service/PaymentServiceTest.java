package com.ecommerce.payment.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentServiceTest {

    @Test
    void amountsEndingInThirteenCentsAreDeclined() {
        assertThat(PaymentService.declined(new BigDecimal("10.13"))).isTrue();
        assertThat(PaymentService.declined(new BigDecimal("0.13"))).isTrue();
        assertThat(PaymentService.declined(new BigDecimal("10.00"))).isFalse();
        assertThat(PaymentService.declined(new BigDecimal("199.90"))).isFalse();
    }
}
