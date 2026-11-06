package com.ecommerce.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "order_tombstones")
public class OrderTombstone {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderTombstone() {
    }

    public OrderTombstone(long orderId, Instant createdAt) {
        this.orderId = orderId;
        this.createdAt = createdAt;
    }
}
