package com.ecommerce.order.service;

import com.ecommerce.common.error.ConflictException;
import com.ecommerce.common.error.ResourceNotFoundException;
import com.ecommerce.order.cache.CartCache;
import com.ecommerce.order.client.ProductCatalogClient;
import com.ecommerce.order.domain.Cart;
import com.ecommerce.order.domain.CartItem;
import com.ecommerce.order.dto.CartItemResponse;
import com.ecommerce.order.dto.CartResponse;
import com.ecommerce.order.dto.OrderLineRequest;
import com.ecommerce.order.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final ProductCatalogClient productCatalogClient;
    private final CartCache cartCache;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public CartService(CartRepository cartRepository, ProductCatalogClient productCatalogClient,
                       CartCache cartCache, PlatformTransactionManager transactionManager, Clock clock) {
        this.cartRepository = cartRepository;
        this.productCatalogClient = productCatalogClient;
        this.cartCache = cartCache;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CartResponse get(long userId) {
        return cartCache.get(userId).orElseGet(() -> {
            CartResponse loaded = load(userId);
            if (!loaded.items().isEmpty()) {
                cartCache.put(loaded);
            }
            return loaded;
        });
    }

    /** Adds quantity to the line. The product must be active. Combined quantity cannot exceed 100. */
    public CartResponse add(long userId, long productId, int quantity) {
        productCatalogClient.getActiveProduct(productId);
        CartResponse updated = transactionTemplate.execute(status -> addLine(userId, productId, quantity));
        cartCache.put(updated);
        return updated;
    }

    public CartResponse setQuantity(long userId, long productId, int quantity) {
        productCatalogClient.getActiveProduct(productId);
        CartResponse updated = transactionTemplate.execute(status -> replaceLine(userId, productId, quantity));
        cartCache.put(updated);
        return updated;
    }

    @Transactional
    public CartResponse remove(long userId, long productId) {
        Cart cart = cartRepository.findWithItemsByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item", productId));
        CartItem item = cart.findItem(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item", productId));
        cart.removeItem(item);
        cart.touch(clock.instant());
        cartCache.evict(userId);
        return toResponse(cart);
    }

    /** Drops every line. Joins the checkout transaction when one is already open. */
    @Transactional
    public void clear(long userId) {
        Cart cart = cartRepository.findWithItemsByUserId(userId)
                .orElseThrow(() -> new ConflictException("EMPTY_CART", "The cart is empty"));
        cart.clear();
        cart.touch(clock.instant());
        cartCache.evict(userId);
    }

    @Transactional(readOnly = true)
    public List<OrderLineRequest> requireLines(long userId) {
        Cart cart = cartRepository.findWithItemsByUserId(userId)
                .orElseThrow(() -> new ConflictException("EMPTY_CART", "The cart is empty"));
        if (cart.getItems().isEmpty()) {
            throw new ConflictException("EMPTY_CART", "The cart is empty");
        }
        return cart.getItems().stream()
                .map(item -> new OrderLineRequest(item.getProductId(), item.getQuantity()))
                .toList();
    }

    private CartResponse addLine(long userId, long productId, int quantity) {
        Cart cart = cartRepository.findWithItemsByUserId(userId)
                .orElseGet(() -> cartRepository.save(new Cart(userId, clock.instant())));
        CartItem item = cart.findItem(productId).orElse(null);
        int next = item == null ? quantity : item.getQuantity() + quantity;
        if (next > CartItem.MAX_QUANTITY) {
            throw new ConflictException("QUANTITY_LIMIT",
                    "A cart line cannot exceed " + CartItem.MAX_QUANTITY + " units");
        }
        if (item == null) {
            cart.addItem(new CartItem(productId, next));
        } else {
            item.setQuantity(next);
        }
        cart.touch(clock.instant());
        return toResponse(cartRepository.save(cart));
    }

    private CartResponse replaceLine(long userId, long productId, int quantity) {
        Cart cart = cartRepository.findWithItemsByUserId(userId)
                .orElseGet(() -> new Cart(userId, clock.instant()));
        cart.findItem(productId).ifPresentOrElse(
                item -> item.setQuantity(quantity),
                () -> cart.addItem(new CartItem(productId, quantity)));
        cart.touch(clock.instant());
        return toResponse(cartRepository.save(cart));
    }

    private CartResponse load(long userId) {
        return cartRepository.findWithItemsByUserId(userId)
                .map(CartService::toResponse)
                .orElseGet(() -> new CartResponse(userId, List.of(), null));
    }

    private static CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(item -> new CartItemResponse(item.getProductId(), item.getQuantity()))
                .toList();
        return new CartResponse(cart.getUserId(), items, cart.getUpdatedAt());
    }
}
