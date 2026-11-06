package com.ecommerce.order.service;

import com.ecommerce.common.error.ApiException;
import com.ecommerce.common.error.ConflictException;
import com.ecommerce.common.error.ResourceNotFoundException;
import com.ecommerce.common.web.PageResponse;
import com.ecommerce.order.client.CatalogProduct;
import com.ecommerce.order.client.ProductCatalogClient;
import com.ecommerce.order.domain.CartItem;
import com.ecommerce.order.domain.CustomerOrder;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.domain.OrderStatusChange;
import com.ecommerce.order.dto.OrderLineRequest;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.repository.CustomerOrderRepository;
import com.ecommerce.order.repository.OrderStatusChangeRepository;
import com.ecommerce.order.saga.OrderEvents;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class OrderService {

    private final CustomerOrderRepository orderRepository;
    private final OrderStatusChangeRepository historyRepository;
    private final ProductCatalogClient productCatalogClient;
    private final CartService cartService;
    private final OrderEvents orderEvents;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public OrderService(CustomerOrderRepository orderRepository, OrderStatusChangeRepository historyRepository,
                        ProductCatalogClient productCatalogClient, CartService cartService, OrderEvents orderEvents,
                        PlatformTransactionManager transactionManager, Clock clock) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.productCatalogClient = productCatalogClient;
        this.cartService = cartService;
        this.orderEvents = orderEvents;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Prices come from the catalog at this moment. The order stays CREATED until the saga moves it. */
    public OrderResponse place(long userId, List<OrderLineRequest> lines) {
        List<PricedLine> priced = price(lines);
        return transactionTemplate.execute(status -> {
            CustomerOrder order = persist(userId, priced);
            orderEvents.orderCreated(order);
            return OrderResponse.from(order);
        });
    }

    /** Orders the current cart, then empties it. A pricing failure leaves the cart unchanged. */
    public OrderResponse checkout(long userId) {
        List<OrderLineRequest> lines = cartService.requireLines(userId);
        List<PricedLine> priced = price(lines);
        return transactionTemplate.execute(status -> {
            List<OrderLineRequest> current = cartService.requireLines(userId);
            if (!current.equals(lines)) {
                throw new ConflictException("CART_CHANGED",
                        "The cart changed while it was being priced; review it and try again");
            }
            CustomerOrder order = persist(userId, priced);
            cartService.clear(userId);
            orderEvents.orderCreated(order);
            return OrderResponse.from(order);
        });
    }

    /**
     * Cancels the caller's order. A CREATED order is cancelled immediately. With the saga running,
     * reserved or unpaid orders are cancelled and stock is released, and a confirmed order stays
     * CONFIRMED until the refund event moves it to REFUNDED.
     */
    @Transactional
    public OrderResponse cancel(long userId, long orderId) {
        CustomerOrder order = requireOwned(userId, orderId);
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED || status == OrderStatus.FAILED) {
            throw new ConflictException("ORDER_NOT_CANCELLABLE",
                    "Order " + orderId + " is " + status + " and cannot be cancelled");
        }
        Instant now = clock.instant();
        if (status == OrderStatus.CONFIRMED) {
            if (!orderEvents.sagaEnabled()) {
                throw notCancellable(orderId, status);
            }
            if (!order.isCancelRequested()) {
                order.requestCancel(now);
                orderEvents.orderCancelled(order, "CUSTOMER_CANCEL", true);
            }
            return OrderResponse.from(order);
        }
        if (status != OrderStatus.CREATED && !orderEvents.sagaEnabled()) {
            throw notCancellable(orderId, status);
        }
        OrderStatus from = status;
        order.cancelBeforeCapture(now);
        order.requestCancel(now);
        historyRepository.save(new OrderStatusChange(order, from, order.getStatus(), "cancelled by customer", now));
        orderEvents.orderCancelled(order, "CUSTOMER_CANCEL", false);
        return OrderResponse.from(order);
    }

    private static ConflictException notCancellable(long orderId, OrderStatus status) {
        return new ConflictException("ORDER_NOT_CANCELLABLE",
                "Order " + orderId + " is " + status + " and cannot be cancelled");
    }

    @Transactional(readOnly = true)
    public OrderResponse get(long userId, long orderId) {
        return OrderResponse.from(requireOwned(userId, orderId));
    }

    @Transactional(readOnly = true)
    public OrderResponse getForAdmin(long orderId) {
        return OrderResponse.from(require(orderId));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(long userId, OrderStatus status, Pageable pageable) {
        Page<CustomerOrder> page = status == null
                ? orderRepository.findByUserId(userId, pageable)
                : orderRepository.findByUserIdAndStatus(userId, status, pageable);
        return PageResponse.from(page, OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> listForAdmin(Long userId, OrderStatus status, Pageable pageable) {
        Page<CustomerOrder> page;
        if (userId != null && status != null) {
            page = orderRepository.findByUserIdAndStatus(userId, status, pageable);
        } else if (userId != null) {
            page = orderRepository.findByUserId(userId, pageable);
        } else if (status != null) {
            page = orderRepository.findByStatus(status, pageable);
        } else {
            page = orderRepository.findAll(pageable);
        }
        return PageResponse.from(page, OrderResponse::from);
    }

    private CustomerOrder persist(long userId, List<PricedLine> priced) {
        Instant now = clock.instant();
        BigDecimal total = priced.stream().map(PricedLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        CustomerOrder order = new CustomerOrder(userId, total, now);
        for (PricedLine line : priced) {
            order.addItem(new OrderItem(line.productId(), line.sku(), line.name(), line.unitPrice(),
                    line.quantity(), line.lineTotal()));
        }
        CustomerOrder saved = orderRepository.saveAndFlush(order);
        historyRepository.save(new OrderStatusChange(saved, null, OrderStatus.CREATED, "order placed", now));
        return saved;
    }

    private List<PricedLine> price(List<OrderLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_ORDER", "An order needs at least one line");
        }
        Map<Long, Integer> quantities = new TreeMap<>();
        for (OrderLineRequest line : lines) {
            quantities.merge(line.productId(), line.quantity(), Math::addExact);
        }
        if (quantities.size() > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_LINES", "An order can contain at most 50 products");
        }
        List<PricedLine> priced = new ArrayList<>(quantities.size());
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            int quantity = entry.getValue();
            if (quantity > CartItem.MAX_QUANTITY) {
                throw new ConflictException("QUANTITY_LIMIT",
                        "A line cannot exceed " + CartItem.MAX_QUANTITY + " units");
            }
            CatalogProduct product = productCatalogClient.getActiveProduct(entry.getKey());
            if (product.price().signum() <= 0) {
                throw new ConflictException("INVALID_PRODUCT_PRICE",
                        "Product " + product.id() + " does not have a positive price");
            }
            BigDecimal unitPrice = product.price().setScale(2, RoundingMode.UNNECESSARY);
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.UNNECESSARY);
            priced.add(new PricedLine(product.id(), product.sku(), product.name(), unitPrice, quantity, lineTotal));
        }
        return priced;
    }

    private CustomerOrder requireOwned(long userId, long orderId) {
        CustomerOrder order = require(orderId);
        if (!order.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Order", orderId);
        }
        return order;
    }

    private CustomerOrder require(long orderId) {
        return orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    private record PricedLine(long productId, String sku, String name, BigDecimal unitPrice, int quantity,
                              BigDecimal lineTotal) {
    }
}
