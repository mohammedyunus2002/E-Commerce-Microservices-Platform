package com.ecommerce.order.web;

import com.ecommerce.common.security.CurrentUser;
import com.ecommerce.order.dto.CartItemRequest;
import com.ecommerce.order.dto.CartResponse;
import com.ecommerce.order.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "Cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    @Operation(summary = "The signed-in customer's cart")
    public CartResponse get(@AuthenticationPrincipal Jwt jwt) {
        return cartService.get(CurrentUser.id(jwt));
    }

    @PostMapping("/items")
    @Operation(summary = "Add units of an active product")
    public CartResponse add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CartItemRequest request) {
        return cartService.add(CurrentUser.id(jwt), request.productId(), request.quantity());
    }

    @PutMapping("/items")
    @Operation(summary = "Set the quantity of a product, adding the line if needed")
    public CartResponse setQuantity(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CartItemRequest request) {
        return cartService.setQuantity(CurrentUser.id(jwt), request.productId(), request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove one product from the cart")
    public CartResponse remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long productId) {
        return cartService.remove(CurrentUser.id(jwt), productId);
    }
}
