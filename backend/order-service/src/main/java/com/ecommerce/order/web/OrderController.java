package com.ecommerce.order.web;

import com.ecommerce.common.error.ApiException;
import com.ecommerce.common.security.CurrentUser;
import com.ecommerce.common.web.PageResponse;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.dto.PlaceOrderRequest;
import com.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/orders")
@Validated
@Tag(name = "Orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Place an order priced from the catalog. Returns 201 CREATED; the client polls for the saga result")
    public ResponseEntity<OrderResponse> place(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse created = orderService.place(CurrentUser.id(jwt), request.lines());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri()
                        .path("/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PostMapping("/checkout")
    @Operation(summary = "Place an order from the cart and empty the cart")
    public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal Jwt jwt) {
        OrderResponse created = orderService.checkout(CurrentUser.id(jwt));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/orders/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping
    @Operation(summary = "The signed-in customer's orders, newest first")
    public PageResponse<OrderResponse> list(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderService.list(CurrentUser.id(jwt), parseStatus(status),
                PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "One of the signed-in customer's orders")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return orderService.get(CurrentUser.id(jwt), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order. Confirmed orders stay CONFIRMED until the refund is recorded")
    public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return orderService.cancel(CurrentUser.id(jwt), id);
    }

    static OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_STATUS",
                    "Unknown order status " + status);
        }
    }
}
