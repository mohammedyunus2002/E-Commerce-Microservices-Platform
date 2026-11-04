package com.ecommerce.gateway.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/**
 * Converts routing failures into the shared JSON error contract. Runs ahead of Spring Boot's
 * default handler, which renders HTML or a different JSON shape.
 */
@Component
@Order(-2)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    private final GatewayErrorWriter errorWriter;

    public GatewayExceptionHandler(GatewayErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        String path = exchange.getRequest().getPath().value();

        if (hasCause(ex, ConnectException.class) || hasCause(ex, UnknownHostException.class)) {
            log.warn("Downstream connection failed for {}: {}", path, rootMessage(ex));
            return errorWriter.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                    "The service handling this request is unavailable. Try again shortly.");
        }
        if (hasCause(ex, TimeoutException.class)) {
            log.warn("Downstream timed out for {}", path);
            return errorWriter.write(exchange, HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT",
                    "The service handling this request did not respond in time");
        }
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            return switch (status) {
                case NOT_FOUND -> errorWriter.write(exchange, status, "ROUTE_NOT_FOUND",
                        "No route matches " + path);
                case SERVICE_UNAVAILABLE -> {
                    log.warn("No available instance for {}: {}", path, rse.getReason());
                    yield errorWriter.write(exchange, status, "SERVICE_UNAVAILABLE",
                            "The service handling this request is unavailable. Try again shortly.");
                }
                case GATEWAY_TIMEOUT -> errorWriter.write(exchange, status, "UPSTREAM_TIMEOUT",
                        "The service handling this request did not respond in time");
                default -> errorWriter.write(exchange, status, status.name(),
                        rse.getReason() != null ? rse.getReason() : status.getReasonPhrase());
            };
        }

        log.error("Unhandled gateway error on {}", path, ex);
        return errorWriter.write(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred");
    }

    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
