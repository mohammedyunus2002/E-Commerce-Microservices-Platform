package com.ecommerce.inventory.repository;

import com.ecommerce.inventory.domain.OrderTombstone;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderTombstoneRepository extends JpaRepository<OrderTombstone, Long> {
}
