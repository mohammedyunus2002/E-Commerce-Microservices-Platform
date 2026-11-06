package com.ecommerce.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productId")
    private List<CartItem> items = new ArrayList<>();

    protected Cart() {
    }

    public Cart(long userId, Instant now) {
        this.userId = userId;
        this.updatedAt = now;
    }

    public Optional<CartItem> findItem(long productId) {
        return items.stream().filter(item -> item.getProductId() == productId).findFirst();
    }

    public void addItem(CartItem item) {
        item.assignCart(this);
        items.add(item);
    }

    public void removeItem(CartItem item) {
        items.remove(item);
    }

    public void clear() {
        items.clear();
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<CartItem> getItems() {
        return List.copyOf(items);
    }
}
