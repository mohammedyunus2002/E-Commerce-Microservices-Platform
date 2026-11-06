package com.ecommerce.payment.repository;

import com.ecommerce.payment.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(long orderId);

    Page<Payment> findByUserId(long userId, Pageable pageable);
}
