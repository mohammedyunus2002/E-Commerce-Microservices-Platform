package com.ecommerce.product.cache;

import com.ecommerce.product.dto.CategoryResponse;
import com.ecommerce.product.dto.ProductResponse;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.List;

/**
 * Redis cache setup. Each cache gets a serializer bound to its exact DTO type, which avoids
 * storing Java class names in Redis and works with records, unlike default polymorphic typing.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheCustomizer(ObjectMapper objectMapper,
                                                                   CacheProperties properties) {
        ObjectMapper mapper = objectMapper.copy();
        JavaType productType = mapper.constructType(ProductResponse.class);
        JavaType categoryListType = mapper.getTypeFactory()
                .constructCollectionType(List.class, CategoryResponse.class);

        return builder -> builder
                // Defer puts and evictions until the surrounding transaction commits, so an
                // eviction cannot run before the database change it is invalidating is visible.
                .transactionAware()
                .withCacheConfiguration(CacheNames.PRODUCTS,
                        cacheConfig(properties.productTtl(), new Jackson2JsonRedisSerializer<>(mapper, productType)))
                .withCacheConfiguration(CacheNames.CATEGORIES,
                        cacheConfig(properties.categoryTtl(),
                                new Jackson2JsonRedisSerializer<>(mapper, categoryListType)));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    private static RedisCacheConfiguration cacheConfig(Duration ttl, Jackson2JsonRedisSerializer<?> serializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("product-service:")
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }
}
