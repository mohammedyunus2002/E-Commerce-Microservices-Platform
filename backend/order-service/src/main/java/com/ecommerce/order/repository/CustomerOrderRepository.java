package com.ecommerce.order.repository;

import com.ecommerce.order.domain.CustomerOrder;
import com.ecommerce.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findWithItemsById(long id);

    @EntityGraph(attributePaths = "items")
    Page<CustomerOrder> findByUserId(long userId, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Page<CustomerOrder> findByUserIdAndStatus(long userId, OrderStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    Page<CustomerOrder> findByStatus(OrderStatus status, Pageable pageable);
}
