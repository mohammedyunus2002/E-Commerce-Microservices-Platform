package com.ecommerce.payment.domain;

public enum PaymentStatus {
    /** The simulated charge was accepted. */
    CAPTURED,
    /** The simulated charge was declined. The row stays so a retry updates it. */
    FAILED,
    /** A captured charge was refunded. */
    REFUNDED
}
