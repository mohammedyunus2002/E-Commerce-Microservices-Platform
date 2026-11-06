package com.ecommerce.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_gates")
public class OrderGate {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    protected OrderGate() {
    }

    public OrderGate(long orderId) {
        this.orderId = orderId;
    }
}
