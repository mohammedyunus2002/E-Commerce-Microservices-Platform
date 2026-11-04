package com.ecommerce.common.error;

import com.ecommerce.common.web.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

public final class ApiErrorFactory {

    private ApiErrorFactory() {
    }

    public static ApiError of(HttpStatus status, String code, String message, HttpServletRequest request) {
        return of(status, code, message, request, List.of());
    }

    public static ApiError of(HttpStatus status,
                              String code,
                              String message,
                              HttpServletRequest request,
                              List<ApiError.FieldViolation> violations) {
        return new ApiError(
                Instant.now(Clock.systemUTC()),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                CorrelationId.current(),
                violations);
    }
}
