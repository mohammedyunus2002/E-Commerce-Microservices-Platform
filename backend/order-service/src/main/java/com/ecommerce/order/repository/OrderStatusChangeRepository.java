package com.ecommerce.order.repository;

import com.ecommerce.order.domain.OrderStatusChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusChangeRepository extends JpaRepository<OrderStatusChange, Long> {

    List<OrderStatusChange> findByOrderIdOrderByCreatedAtAsc(long orderId);
}
