package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Every quantity change is a single conditional UPDATE. The database row lock serializes
 * concurrent writers, and the WHERE clause rejects any change that would go negative.
 * Each method returns the number of rows changed: 0 means the condition did not hold.
 */
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

    List<InventoryItem> findByProductIdIn(Collection<Long> productIds);

    Page<InventoryItem> findByAvailableQuantityLessThanEqual(int threshold, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InventoryItem i
               set i.availableQuantity = i.availableQuantity - :quantity,
                   i.reservedQuantity  = i.reservedQuantity + :quantity,
                   i.updatedAt         = :now
             where i.productId = :productId
               and i.availableQuantity >= :quantity
            """)
    int reserve(long productId, int quantity, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InventoryItem i
               set i.availableQuantity = i.availableQuantity + :quantity,
                   i.reservedQuantity  = i.reservedQuantity - :quantity,
                   i.updatedAt         = :now
             where i.productId = :productId
               and i.reservedQuantity >= :quantity
            """)
    int release(long productId, int quantity, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InventoryItem i
               set i.reservedQuantity = i.reservedQuantity - :quantity,
                   i.updatedAt        = :now
             where i.productId = :productId
               and i.reservedQuantity >= :quantity
            """)
    int commitReserved(long productId, int quantity, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InventoryItem i
               set i.availableQuantity = :quantity,
                   i.updatedAt         = :now
             where i.productId = :productId
            """)
    int setAvailable(long productId, int quantity, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update InventoryItem i
               set i.availableQuantity = i.availableQuantity + :delta,
                   i.updatedAt         = :now
             where i.productId = :productId
               and i.availableQuantity + :delta >= 0
            """)
    int adjustAvailable(long productId, int delta, Instant now);
}
