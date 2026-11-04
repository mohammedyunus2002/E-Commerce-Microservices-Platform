package com.ecommerce.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayExceptionHandlerTest {

    private final GatewayExceptionHandler handler = new GatewayExceptionHandler(
            new GatewayErrorWriter(new ObjectMapper().registerModule(new JavaTimeModule())));

    @Test
    void unresolvableServiceHostIsServiceUnavailable() {
        assertThat(statusFor(new RuntimeException(new UnknownHostException("order-service"))))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void refusedConnectionIsServiceUnavailable() {
        assertThat(statusFor(new ConnectException("Connection refused")))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void timeoutIsGatewayTimeout() {
        assertThat(statusFor(new TimeoutException())).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void responseStatusIsPreserved() {
        assertThat(statusFor(new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE)))
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void unknownErrorsAreInternalAndDoNotLeakDetails() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/x"));

        handler.handle(exchange, new IllegalStateException("secret internal detail")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("INTERNAL_ERROR")
                .doesNotContain("secret internal detail");
    }

    private HttpStatus statusFor(Throwable error) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/orders"));
        handler.handle(exchange, error).block();
        return HttpStatus.valueOf(exchange.getResponse().getStatusCode().value());
    }
}
