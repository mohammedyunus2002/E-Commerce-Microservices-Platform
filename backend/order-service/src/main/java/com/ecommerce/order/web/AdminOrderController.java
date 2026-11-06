package com.ecommerce.order.web;

import com.ecommerce.common.web.PageResponse;
import com.ecommerce.order.dto.OrderResponse;
import com.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@Tag(name = "Order administration")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @Operation(summary = "Every order, optionally filtered by customer or status")
    public PageResponse<OrderResponse> list(@RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderService.listForAdmin(userId, OrderController.parseStatus(status),
                PageRequest.of(page, size, Sort.by("createdAt").descending().and(Sort.by("id").descending())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Any order")
    public OrderResponse get(@PathVariable long id) {
        return orderService.getForAdmin(id);
    }
}
