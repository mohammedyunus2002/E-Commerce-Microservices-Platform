package com.ecommerce.order.saga;

import com.ecommerce.order.domain.CustomerOrder;

/** Publishes the events the order aggregate owns. A no-op implementation is used when the saga is off. */
public interface OrderEvents {

    boolean sagaEnabled();

    void orderCreated(CustomerOrder order);

    void orderCancelled(CustomerOrder order, String reason, boolean refundRequested);
}
