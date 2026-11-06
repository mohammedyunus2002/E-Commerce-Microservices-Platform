package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.OrderGate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderGateRepository extends JpaRepository<OrderGate, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from OrderGate g where g.orderId = :orderId")
    Optional<OrderGate> findForUpdate(@Param("orderId") long orderId);
}
