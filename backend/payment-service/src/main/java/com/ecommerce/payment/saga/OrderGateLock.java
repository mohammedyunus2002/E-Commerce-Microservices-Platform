package com.ecommerce.payment.saga;

import com.ecommerce.payment.domain.OrderGate;
import com.ecommerce.payment.repository.OrderGateRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OrderGateLock {

    private final OrderGateRepository repository;
    private final TransactionTemplate requiresNew;

    public OrderGateLock(OrderGateRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void lock(long orderId, Runnable action) {
        if (!repository.existsById(orderId)) {
            try {
                requiresNew.executeWithoutResult(status -> repository.saveAndFlush(new OrderGate(orderId)));
            } catch (DataIntegrityViolationException ignored) {
                // Another transaction inserted the same gate.
            }
        }
        if (repository.findForUpdate(orderId).isEmpty()) {
            throw new IllegalStateException("Order gate " + orderId + " is missing");
        }
        action.run();
    }
}
