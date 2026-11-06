package com.ecommerce.payment.service;

import com.ecommerce.common.error.ConflictException;
import com.ecommerce.common.error.ResourceNotFoundException;
import com.ecommerce.common.web.PageResponse;
import com.ecommerce.payment.client.OrderClient;
import com.ecommerce.payment.client.OrderSnapshot;
import com.ecommerce.payment.domain.Payment;
import com.ecommerce.payment.domain.PaymentStatus;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * Simulated acquirer. A charge whose amount ends in {@code .13} is declined; every other positive
 * amount is captured. This does not change the order. The saga does that in Phase 5.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OrderClient orderClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository, OrderClient orderClient,
                          PlatformTransactionManager transactionManager, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.orderClient = orderClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** @return the payment, and whether this call stored a new row */
    public ChargeOutcome charge(long userId, long orderId, String bearerToken) {
        OrderSnapshot order = orderClient.getOwnOrder(orderId, bearerToken);
        if (!order.userId().equals(userId)) {
            throw new ResourceNotFoundException("Order", orderId);
        }
        if (!"CREATED".equals(order.status())) {
            throw new ConflictException("ORDER_NOT_PAYABLE",
                    "Order " + orderId + " is " + order.status() + " and cannot be charged");
        }
        boolean declined = declined(order.totalAmount());
        return transactionTemplate.execute(status -> store(order, userId, declined));
    }

    private ChargeOutcome store(OrderSnapshot order, long userId, boolean declined) {
        Instant now = clock.instant();
        long orderId = order.id();
        Payment existing = paymentRepository.findByOrderId(orderId).orElse(null);
        if (existing == null) {
            Payment created = paymentRepository.saveAndFlush(new Payment(orderId, userId, order.totalAmount(),
                    order.currency(), declined, now));
            log.info("Order {} payment {} {}", orderId, created.getId(), created.getStatus());
            return new ChargeOutcome(PaymentResponse.from(created), true);
        }
        if (existing.getStatus() == PaymentStatus.CAPTURED || existing.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Order {} already has payment {} in status {}", orderId, existing.getId(), existing.getStatus());
            return new ChargeOutcome(PaymentResponse.from(existing), false);
        }
        existing.retry(order.totalAmount(), declined, now);
        log.info("Order {} payment {} retried as {}", orderId, existing.getId(), existing.getStatus());
        return new ChargeOutcome(PaymentResponse.from(existing), false);
    }

    @Transactional
    public PaymentResponse refund(long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", orderId));
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return PaymentResponse.from(payment);
        }
        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            throw new ConflictException("PAYMENT_NOT_REFUNDABLE",
                    "Payment for order " + orderId + " is " + payment.getStatus() + " and cannot be refunded");
        }
        payment.refund(clock.instant());
        log.info("Order {} payment {} refunded", orderId, payment.getId());
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> listOwn(long userId, Pageable pageable) {
        return PageResponse.from(paymentRepository.findByUserId(userId, pageable), PaymentResponse::from);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getOwn(long userId, long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", orderId));
        if (!payment.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Payment", orderId);
        }
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> listAll(Pageable pageable) {
        return PageResponse.from(paymentRepository.findAll(pageable), PaymentResponse::from);
    }

    /**
     * Records the saga charge using the amount from the event. Must run inside the caller's transaction.
     * A payment that is already captured or refunded is returned unchanged.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment recordSagaCharge(long orderId, long userId, BigDecimal amount, String currency) {
        boolean declined = declined(amount);
        Instant now = clock.instant();
        Payment existing = paymentRepository.findByOrderId(orderId).orElse(null);
        if (existing == null) {
            Payment created = paymentRepository.saveAndFlush(new Payment(orderId, userId, amount, currency, declined, now));
            log.info("Saga charge for order {} stored as {}", orderId, created.getStatus());
            return created;
        }
        if (existing.getStatus() == PaymentStatus.FAILED) {
            existing.retry(amount, declined, now);
            log.info("Saga charge for order {} retried as {}", orderId, existing.getStatus());
        }
        return existing;
    }

    /** Refunds a captured payment. A missing or non-captured payment is returned as it is. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment refundCaptured(long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.CAPTURED) {
            return payment;
        }
        payment.refund(clock.instant());
        log.info("Saga refunded payment for order {}", orderId);
        return payment;
    }

    /** Amounts such as 10.13 and 0.13 are the deterministic decline. */
    static boolean declined(BigDecimal amount) {
        return amount.movePointRight(2).remainder(BigDecimal.valueOf(100)).intValue() == 13;
    }

    public record ChargeOutcome(PaymentResponse payment, boolean created) {
    }
}
