package com.ecommerce.product.cache;

import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThatNoException;

class LoggingCacheErrorHandlerTest {

    private final LoggingCacheErrorHandler handler = new LoggingCacheErrorHandler();
    private final ConcurrentMapCache cache = new ConcurrentMapCache("products");
    private final RedisConnectionFailureException redisDown = new RedisConnectionFailureException("down");

    @Test
    void redisFailuresDoNotPropagateToCallers() {
        assertThatNoException().isThrownBy(() -> {
            handler.handleCacheGetError(redisDown, cache, 1L);
            handler.handleCachePutError(redisDown, cache, 1L, "value");
            handler.handleCacheEvictError(redisDown, cache, 1L);
            handler.handleCacheClearError(redisDown, cache);
        });
    }
}
