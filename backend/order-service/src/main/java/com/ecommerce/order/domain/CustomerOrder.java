package com.ecommerce.order.domain;

import com.ecommerce.common.error.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The order aggregate. Status changes go through the methods below so illegal jumps are rejected.
 * Phase 5's saga is the caller for every transition except cancelling a {@code CREATED} order.
 */
@Entity
@Table(name = "customer_orders")
public class CustomerOrder {

    public static final String CURRENCY = "USD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productId")
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() {
    }

    public CustomerOrder(long userId, BigDecimal totalAmount, Instant now) {
        this.userId = userId;
        this.status = OrderStatus.CREATED;
        this.currency = CURRENCY;
        this.totalAmount = totalAmount;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addItem(OrderItem item) {
        item.assignOrder(this);
        items.add(item);
    }

    public void reserveInventory(Instant now) {
        move(OrderStatus.CREATED, OrderStatus.INVENTORY_RESERVED, now);
    }

    public void requestPayment(Instant now) {
        move(OrderStatus.INVENTORY_RESERVED, OrderStatus.PAYMENT_PENDING, now);
    }

    public void confirm(Instant now) {
        move(OrderStatus.PAYMENT_PENDING, OrderStatus.CONFIRMED, now);
    }

    /** Reservation rejected, or payment failed before capture. */
    public void fail(Instant now) {
        if (status != OrderStatus.CREATED && status != OrderStatus.PAYMENT_PENDING) {
            throw new ConflictException("ORDER_NOT_FAILABLE",
                    "Order " + id + " is " + status + " and cannot fail");
        }
        status = OrderStatus.FAILED;
        updatedAt = now;
    }

    /**
     * Cancel before money is captured. Callers that still hold stock must release it themselves;
     * this method only changes the order.
     */
    public void cancelBeforeCapture(Instant now) {
        if (status != OrderStatus.CREATED
                && status != OrderStatus.INVENTORY_RESERVED
                && status != OrderStatus.PAYMENT_PENDING) {
            throw new ConflictException("ORDER_NOT_CANCELLABLE",
                    "Order " + id + " is " + status + " and cannot be cancelled");
        }
        status = OrderStatus.CANCELLED;
        updatedAt = now;
    }

    public void refund(Instant now) {
        move(OrderStatus.CONFIRMED, OrderStatus.REFUNDED, now);
    }

    public void requestCancel(Instant now) {
        this.cancelRequested = true;
        this.updatedAt = now;
    }

    private void move(OrderStatus from, OrderStatus to, Instant now) {
        if (status != from) {
            throw new ConflictException("INVALID_ORDER_TRANSITION",
                    "Order " + id + " is " + status + ", expected " + from + " to become " + to);
        }
        status = to;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }
}
