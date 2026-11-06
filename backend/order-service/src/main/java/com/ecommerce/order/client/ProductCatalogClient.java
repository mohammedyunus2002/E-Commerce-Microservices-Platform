package com.ecommerce.order.client;

import com.ecommerce.common.error.ApiException;
import com.ecommerce.common.error.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Reads the public catalog. Inactive products are 404 there, so they cannot be ordered. */
@Component
public class ProductCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogClient.class);

    private final RestClient restClient;

    public ProductCatalogClient(RestClient productServiceRestClient) {
        this.restClient = productServiceRestClient;
    }

    public CatalogProduct getActiveProduct(long productId) {
        try {
            CatalogProduct product = restClient.get()
                    .uri("/api/products/{id}", productId)
                    .retrieve()
                    .body(CatalogProduct.class);
            if (product == null || product.id() == null || product.price() == null || !product.active()) {
                throw new ResourceNotFoundException("Product", productId);
            }
            return product;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Product", productId);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.warn("product-service answered {} for product {}", e.getStatusCode(), productId);
            throw unavailable();
        } catch (ResourceAccessException e) {
            log.warn("product-service unreachable for product {}: {}", productId, e.getMessage());
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_SERVICE_UNAVAILABLE",
                "The product catalog could not be checked; try again shortly");
    }
}
