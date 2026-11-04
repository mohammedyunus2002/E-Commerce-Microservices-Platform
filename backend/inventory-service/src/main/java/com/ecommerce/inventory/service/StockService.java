package com.ecommerce.inventory.service;

import com.ecommerce.common.error.ResourceNotFoundException;
import com.ecommerce.common.web.PageResponse;
import com.ecommerce.inventory.client.ProductCatalogClient;
import com.ecommerce.inventory.domain.InventoryItem;
import com.ecommerce.inventory.dto.AvailabilityResponse;
import com.ecommerce.inventory.dto.InventoryItemResponse;
import com.ecommerce.inventory.repository.InventoryItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Stock lookups and the admin operations that change sellable quantities. */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final InventoryItemRepository repository;
    private final ProductCatalogClient productCatalogClient;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public StockService(InventoryItemRepository repository,
                        ProductCatalogClient productCatalogClient,
                        PlatformTransactionManager transactionManager,
                        Clock clock) {
        this.repository = repository;
        this.productCatalogClient = productCatalogClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(long productId) {
        return AvailabilityResponse.of(productId,
                repository.findById(productId).map(InventoryItem::getAvailableQuantity).orElse(0));
    }

    /** Returns one entry per distinct requested ID, in the order requested. */
    @Transactional(readOnly = true)
    public List<AvailabilityResponse> availability(Collection<Long> productIds) {
        Map<Long, InventoryItem> items = repository.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, Function.identity()));
        return productIds.stream()
                .distinct()
                .map(id -> AvailabilityResponse.of(id,
                        items.containsKey(id) ? items.get(id).getAvailableQuantity() : 0))
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse get(long productId) {
        return InventoryItemResponse.from(find(productId));
    }

    /** All stock records, or only those at or below {@code lowStockThreshold} when it is given. */
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> list(Integer lowStockThreshold, Pageable pageable) {
        Page<InventoryItem> page = lowStockThreshold == null
                ? repository.findAll(pageable)
                : repository.findByAvailableQuantityLessThanEqual(lowStockThreshold, pageable);
        return PageResponse.from(page, InventoryItemResponse::from);
    }

    /**
     * Sets sellable stock, creating the record on first use. The product-service check runs
     * before the transaction opens so no database connection is held during the HTTP call.
     */
    public InventoryItemResponse setAvailable(long productId, int quantity, long adminId, String bearerToken) {
        if (!repository.existsById(productId)) {
            productCatalogClient.requireProduct(productId, bearerToken);
        }
        InventoryItemResponse result = transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            if (repository.setAvailable(productId, quantity, now) == 0) {
                repository.saveAndFlush(InventoryItem.create(productId, quantity, now));
            }
            return InventoryItemResponse.from(find(productId));
        });
        log.info("Admin {} set available stock of product {} to {}", adminId, productId, quantity);
        return result;
    }

    /** Applies a relative change; refuses changes that would make available stock negative. */
    @Transactional
    public InventoryItemResponse adjust(long productId, int delta, String reason, long adminId) {
        if (repository.adjustAvailable(productId, delta, clock.instant()) == 0) {
            InventoryItem item = find(productId);
            throw new InsufficientStockException(productId, -delta, item.getAvailableQuantity());
        }
        InventoryItem item = find(productId);
        log.info("Admin {} adjusted stock of product {} by {} to {} (reason: {})",
                adminId, productId, delta, item.getAvailableQuantity(), reason);
        return InventoryItemResponse.from(item);
    }

    private InventoryItem find(long productId) {
        return repository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item", productId));
    }
}
