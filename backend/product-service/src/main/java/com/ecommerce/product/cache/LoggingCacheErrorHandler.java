package com.ecommerce.product.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Treats the cache as an optimization: if Redis is down or returns bad data, the request
 * falls through to MySQL instead of failing.
 */
public class LoggingCacheErrorHandler implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingCacheErrorHandler.class);

    @Override
    public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
        log.warn("Cache read failed for {}::{}; using the database: {}", cache.getName(), key, ex.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
        log.warn("Cache write failed for {}::{}: {}", cache.getName(), key, ex.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
        // A failed eviction can leave a stale entry until its TTL expires; log loudly.
        log.error("Cache eviction failed for {}::{}; entry may be stale until TTL: {}",
                cache.getName(), key, ex.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException ex, Cache cache) {
        log.error("Cache clear failed for {}; entries may be stale until TTL: {}", cache.getName(), ex.getMessage());
    }
}
