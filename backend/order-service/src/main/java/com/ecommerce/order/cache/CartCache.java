package com.ecommerce.order.cache;

import com.ecommerce.order.dto.CartResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Read-through cache for a user's cart. MySQL is the source of truth: a Redis failure is logged
 * and the caller uses the database.
 */
@Component
public class CartCache {

    private static final Logger log = LoggerFactory.getLogger(CartCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final CartCacheProperties properties;

    public CartCache(StringRedisTemplate redis, ObjectMapper objectMapper, CartCacheProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<CartResponse> get(long userId) {
        if (!properties.cacheEnabled()) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(key(userId));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, CartResponse.class));
        } catch (Exception e) {
            log.warn("Cart cache read failed for user {}; using the database: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(CartResponse cart) {
        if (!properties.cacheEnabled()) {
            return;
        }
        try {
            redis.opsForValue().set(key(cart.userId()), objectMapper.writeValueAsString(cart), properties.cacheTtl());
        } catch (JsonProcessingException e) {
            log.warn("Cart cache write skipped for user {}: {}", cart.userId(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Cart cache write failed for user {}: {}", cart.userId(), e.getMessage());
        }
    }

    public void evict(long userId) {
        if (!properties.cacheEnabled()) {
            return;
        }
        try {
            redis.delete(key(userId));
        } catch (RuntimeException e) {
            log.warn("Cart cache eviction failed for user {}: {}", userId, e.getMessage());
        }
    }

    static String key(long userId) {
        return "order-service:carts::" + userId;
    }
}
