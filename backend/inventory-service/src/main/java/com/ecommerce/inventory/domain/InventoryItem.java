package com.ecommerce.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * Stock for one product. Quantities are changed only through the conditional bulk updates in
 * {@code InventoryItemRepository}, so concurrent writers never overwrite each other's changes.
 */
@Entity
@Table(name = "inventory_items")
public class InventoryItem implements Persistable<Long> {

    @Id
    @Column(name = "product_id")
    private Long productId;

    /** Units that can still be sold. */
    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    /** Units held for orders that have not completed. */
    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // The ID is the product ID, assigned by us, so tell Spring Data when to INSERT instead of merge.
    @Transient
    private boolean isNew;

    protected InventoryItem() {
    }

    public static InventoryItem create(long productId, int availableQuantity, Instant now) {
        InventoryItem item = new InventoryItem();
        item.productId = productId;
        item.availableQuantity = availableQuantity;
        item.reservedQuantity = 0;
        item.createdAt = now;
        item.updatedAt = now;
        item.isNew = true;
        return item;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    @Override
    public Long getId() {
        return productId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public Long getProductId() {
        return productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
