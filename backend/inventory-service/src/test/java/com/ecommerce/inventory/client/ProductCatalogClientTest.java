package com.ecommerce.inventory.client;

import com.ecommerce.common.error.ApiException;
import com.ecommerce.common.error.ResourceNotFoundException;
import com.ecommerce.common.web.CorrelationId;
import com.ecommerce.common.web.CorrelationIdClientInterceptor;
import com.ecommerce.inventory.config.ProductClientConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProductCatalogClientTest {

    private MockRestServiceServer server;
    private ProductCatalogClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://product-service")
                .requestInterceptor(new CorrelationIdClientInterceptor());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ProductCatalogClient(builder.build());
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void relaysBearerTokenAndCorrelationId() {
        MDC.put(CorrelationId.MDC_KEY, "corr-123");
        server.expect(requestTo("http://product-service/api/admin/products/7"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(header(CorrelationId.HEADER, "corr-123"))
                .andRespond(withSuccess("{\"id\":7}", MediaType.APPLICATION_JSON));

        client.requireProduct(7L, "admin-token");

        server.verify();
    }

    @Test
    void missingProductBecomesNotFound() {
        server.expect(requestTo("http://product-service/api/admin/products/8"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.requireProduct(8L, "admin-token"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void serverErrorBecomesServiceUnavailable() {
        server.expect(requestTo("http://product-service/api/admin/products/9")).andRespond(withServerError());

        assertThatThrownBy(() -> client.requireProduct(9L, "admin-token"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getCode()).isEqualTo("PRODUCT_SERVICE_UNAVAILABLE");
                });
    }

    @Test
    void unreachableServiceBecomesServiceUnavailable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        RestClient restClient = new ProductClientConfig().productServiceRestClient(RestClient.builder(),
                new ProductServiceProperties("http://localhost:" + closedPort,
                        Duration.ofSeconds(1), Duration.ofSeconds(1)));

        assertThatThrownBy(() -> new ProductCatalogClient(restClient).requireProduct(1L, "admin-token"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getCode()).isEqualTo("PRODUCT_SERVICE_UNAVAILABLE"));
    }
}
