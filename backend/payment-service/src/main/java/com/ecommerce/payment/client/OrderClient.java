package com.ecommerce.payment.client;

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

@Component
public class OrderClient {

    private static final Logger log = LoggerFactory.getLogger(OrderClient.class);

    private final RestClient restClient;

    public OrderClient(RestClient orderServiceRestClient) {
        this.restClient = orderServiceRestClient;
    }

    public OrderSnapshot getOwnOrder(long orderId, String bearerToken) {
        try {
            OrderSnapshot order = restClient.get()
                    .uri("/api/orders/{id}", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .retrieve()
                    .body(OrderSnapshot.class);
            if (order == null || order.id() == null || order.totalAmount() == null) {
                throw new ResourceNotFoundException("Order", orderId);
            }
            return order;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Order", orderId);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.warn("order-service answered {} for order {}", e.getStatusCode(), orderId);
            throw unavailable();
        } catch (ResourceAccessException e) {
            log.warn("order-service unreachable for order {}: {}", orderId, e.getMessage());
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ORDER_SERVICE_UNAVAILABLE",
                "The order could not be checked; try again shortly");
    }
}
