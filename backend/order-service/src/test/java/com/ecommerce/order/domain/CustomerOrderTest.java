package com.ecommerce.order.domain;

import com.ecommerce.common.error.ConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerOrderTest {

    private final Instant now = Instant.parse("2026-11-06T10:00:00Z");

    @Test
    void happyPathReachesConfirmedAndThenRefunded() {
        CustomerOrder order = new CustomerOrder(7L, new BigDecimal("20.00"), now);
        order.reserveInventory(now);
        order.requestPayment(now);
        order.confirm(now);
        order.refund(now);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void createdOrderCanBeCancelledAndCannotBeConfirmed() {
        CustomerOrder order = new CustomerOrder(7L, new BigDecimal("20.00"), now);
        order.cancelBeforeCapture(now);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> order.confirm(now))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("INVALID_ORDER_TRANSITION");
    }

    @Test
    void confirmedOrderCannotBeCancelled() {
        CustomerOrder order = confirmed();
        assertThatThrownBy(() -> order.cancelBeforeCapture(now))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("ORDER_NOT_CANCELLABLE");
    }

    @Test
    void paymentPendingCanFail() {
        CustomerOrder order = new CustomerOrder(7L, new BigDecimal("20.00"), now);
        order.reserveInventory(now);
        order.requestPayment(now);
        order.fail(now);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    private CustomerOrder confirmed() {
        CustomerOrder order = new CustomerOrder(7L, new BigDecimal("20.00"), now);
        order.reserveInventory(now);
        order.requestPayment(now);
        order.confirm(now);
        return order;
    }
}
