package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.ReservationStatus;
import com.ecommerce.inventory.domain.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {

    List<StockReservation> findByOrderIdOrderByProductIdAsc(long orderId);

    /**
     * Compare-and-set on status. Returns 1 only for the caller that performed the transition,
     * so stock is credited back exactly once even if a release is processed twice.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update StockReservation r
               set r.status = :to, r.updatedAt = :now
             where r.id = :id and r.status = :from
            """)
    int transition(long id, ReservationStatus from, ReservationStatus to, Instant now);
}
