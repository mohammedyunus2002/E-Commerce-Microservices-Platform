package com.ecommerce.gateway.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Assigns each request a correlation ID before security runs, forwards it to downstream services,
 * returns it to the client, and writes one access-log line per request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String HEADER = "X-Correlation-Id";
    private static final String ATTRIBUTE = CorrelationIdWebFilter.class.getName() + ".id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger accessLog = LoggerFactory.getLogger("gateway.access");

    public static String correlationId(ServerWebExchange exchange) {
        return exchange.getAttribute(ATTRIBUTE);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER, correlationId))
                .build();
        ServerWebExchange mutated = exchange.mutate().request(request).build();
        mutated.getAttributes().put(ATTRIBUTE, correlationId);
        mutated.getResponse().getHeaders().set(HEADER, correlationId);

        long startNanos = System.nanoTime();
        return chain.filter(mutated).doFinally(signal -> accessLog.info(
                "{} {} -> {} in {} ms [correlationId={}]",
                request.getMethod(),
                request.getPath().value(),
                mutated.getResponse().getStatusCode() != null
                        ? mutated.getResponse().getStatusCode().value()
                        : "-",
                (System.nanoTime() - startNanos) / 1_000_000,
                correlationId));
    }
}
