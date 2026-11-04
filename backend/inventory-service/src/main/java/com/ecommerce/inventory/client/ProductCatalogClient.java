package com.ecommerce.inventory.client;

import com.ecommerce.common.error.ApiException;
import com.ecommerce.common.error.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Asks product-service whether a product exists. Used only when an admin creates a stock record,
 * so inventory never holds stock for a product ID that was never in the catalog.
 */
@Component
public class ProductCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogClient.class);

    private final RestClient restClient;

    public ProductCatalogClient(RestClient productServiceRestClient) {
        this.restClient = productServiceRestClient;
    }

    /**
     * @param bearerToken the calling admin's access token, relayed because the endpoint is admin-only
     * @throws ResourceNotFoundException if product-service has no such product
     * @throws ApiException 503 if product-service cannot be reached or fails
     */
    public void requireProduct(long productId, String bearerToken) {
        try {
            restClient.get()
                    .uri("/api/admin/products/{id}", productId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Product", productId);
        } catch (RestClientResponseException e) {
            log.warn("product-service answered {} when checking product {}", e.getStatusCode(), productId);
            throw unavailable();
        } catch (ResourceAccessException e) {
            log.warn("product-service unreachable when checking product {}: {}", productId, e.getMessage());
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_SERVICE_UNAVAILABLE",
                "The product catalog could not be checked; try again shortly");
    }
}
