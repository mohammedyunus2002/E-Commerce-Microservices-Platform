package com.ecommerce.payment.repository;

import com.ecommerce.payment.domain.OrderTombstone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderTombstoneRepository extends JpaRepository<OrderTombstone, Long> {
}
