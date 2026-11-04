package com.ecommerce.inventory.web;

import com.ecommerce.inventory.dto.AvailabilityResponse;
import com.ecommerce.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory/availability")
@Validated
@Tag(name = "Availability")
public class AvailabilityController {

    private final StockService stockService;

    public AvailabilityController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Units available for one product (0 if it has no stock record)")
    public AvailabilityResponse availability(@PathVariable long productId) {
        return stockService.availability(productId);
    }

    @GetMapping
    @Operation(summary = "Units available for up to 100 products, e.g. a catalog page or cart")
    public List<AvailabilityResponse> availability(
            @RequestParam @NotEmpty @Size(max = 100) List<Long> productIds) {
        return stockService.availability(productIds);
    }
}
