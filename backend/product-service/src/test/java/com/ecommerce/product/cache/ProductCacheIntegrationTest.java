package com.ecommerce.product.cache;

import com.ecommerce.product.dto.CategoryRequest;
import com.ecommerce.product.dto.CreateProductRequest;
import com.ecommerce.product.dto.ProductResponse;
import com.ecommerce.product.dto.UpdateProductRequest;
import com.ecommerce.product.repository.ProductRepository;
import com.ecommerce.product.service.CategoryService;
import com.ecommerce.product.service.ProductService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Runs the real Redis cache against a Redis 7 container: hits skip MySQL, writes evict,
 * and entries carry the configured TTL.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.cache.type=redis")
@Testcontainers(disabledWithoutDocker = true)
class ProductCacheIntegrationTest {

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @Autowired
    private ProductService productService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private MeterRegistry meterRegistry;
    @MockitoSpyBean
    private ProductRepository productRepository;

    private long productId;

    @BeforeEach
    void setUp() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        long categoryId = categoryService.create(new CategoryRequest("Cache Cat " + System.nanoTime(), null)).id();
        productId = productService.create(new CreateProductRequest(
                "CACHE-" + System.nanoTime() % 1_000_000, "Cached", null, new BigDecimal("10.00"), null, categoryId))
                .id();
        clearInvocations(productRepository);
    }

    @Test
    void secondReadIsServedFromRedis() {
        ProductResponse first = productService.getProduct(productId);
        ProductResponse second = productService.getProduct(productId);

        assertThat(second).isEqualTo(first);
        verify(productRepository, times(1)).findWithCategoryById(productId);

        String key = "product-service:products::" + productId;
        assertThat(redis.opsForValue().get(key)).contains("\"name\":\"Cached\"").doesNotContain("@class");
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 600L);

        assertThat(cacheGets("hit")).isGreaterThanOrEqualTo(1);
        assertThat(cacheGets("miss")).isGreaterThanOrEqualTo(1);
    }

    private double cacheGets(String result) {
        return meterRegistry.get("cache.gets").tag("cache", CacheNames.PRODUCTS).tag("result", result)
                .functionCounter().count();
    }

    @Test
    void updateEvictsSoNextReadSeesNewData() {
        ProductResponse cached = productService.getProduct(productId);

        productService.update(productId, new UpdateProductRequest(
                "Renamed", null, new BigDecimal("12.00"), null, cached.category().id(), true));

        assertThat(redis.hasKey("product-service:products::" + productId)).isFalse();
        assertThat(productService.getProduct(productId).name()).isEqualTo("Renamed");
    }

    @Test
    void categoryListIsCachedAndEvictedOnChange() {
        int before = categoryService.listCategories().size();
        assertThat(redis.hasKey("product-service:categories::all")).isTrue();

        categoryService.create(new CategoryRequest("Another " + System.nanoTime(), null));

        assertThat(redis.hasKey("product-service:categories::all")).isFalse();
        assertThat(categoryService.listCategories()).hasSize(before + 1);
    }
}
