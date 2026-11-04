package com.ecommerce.inventory.web;

import com.ecommerce.common.security.CurrentUser;
import com.ecommerce.common.web.PageResponse;
import com.ecommerce.inventory.dto.AdjustStockRequest;
import com.ecommerce.inventory.dto.InventoryItemResponse;
import com.ecommerce.inventory.dto.ReservationResponse;
import com.ecommerce.inventory.dto.SetStockRequest;
import com.ecommerce.inventory.service.ReservationService;
import com.ecommerce.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/inventory")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@Tag(name = "Inventory administration")
public class AdminInventoryController {

    private final StockService stockService;
    private final ReservationService reservationService;

    public AdminInventoryController(StockService stockService, ReservationService reservationService) {
        this.stockService = stockService;
        this.reservationService = reservationService;
    }

    @GetMapping
    @Operation(summary = "List stock records, lowest available first; optionally only those at or below a threshold")
    public PageResponse<InventoryItemResponse> list(
            @RequestParam(required = false) @Min(0) Integer lowStockThreshold,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Sort sort = Sort.by("availableQuantity").ascending().and(Sort.by("productId"));
        return stockService.list(lowStockThreshold, PageRequest.of(page, size, sort));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Stock record for one product, including reserved units")
    public InventoryItemResponse get(@PathVariable long productId) {
        return stockService.get(productId);
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Set available units; creates the record after checking the product exists")
    public InventoryItemResponse set(@PathVariable long productId,
                                     @Valid @RequestBody SetStockRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return stockService.setAvailable(productId, request.availableQuantity(), CurrentUser.id(jwt),
                jwt.getTokenValue());
    }

    @PostMapping("/{productId}/adjust")
    @Operation(summary = "Add or remove available units; rejected if stock would go negative")
    public InventoryItemResponse adjust(@PathVariable long productId,
                                        @Valid @RequestBody AdjustStockRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return stockService.adjust(productId, request.delta(), request.reason(), CurrentUser.id(jwt));
    }

    @GetMapping("/reservations")
    @Operation(summary = "Reservation lines held for an order")
    public List<ReservationResponse> reservations(@RequestParam long orderId) {
        return reservationService.findByOrder(orderId);
    }
}
