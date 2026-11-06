package com.ecommerce.payment.web;

import com.ecommerce.common.security.CurrentUser;
import com.ecommerce.common.web.PageResponse;
import com.ecommerce.payment.dto.ChargeRequest;
import com.ecommerce.payment.dto.PaymentResponse;
import com.ecommerce.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/payments")
@Validated
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Charge the signed-in customer's CREATED order for its catalog total")
    public ResponseEntity<PaymentResponse> charge(@AuthenticationPrincipal Jwt jwt,
                                                  @Valid @RequestBody ChargeRequest request) {
        PaymentService.ChargeOutcome outcome = paymentService.charge(CurrentUser.id(jwt), request.orderId(),
                jwt.getTokenValue());
        if (!outcome.created()) {
            return ResponseEntity.ok(outcome.payment());
        }
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri()
                        .path("/{orderId}").buildAndExpand(outcome.payment().orderId()).toUri())
                .body(outcome.payment());
    }

    @GetMapping
    @Operation(summary = "The signed-in customer's payments, newest first")
    public PageResponse<PaymentResponse> list(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return paymentService.listOwn(CurrentUser.id(jwt),
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "The payment stored for one of the signed-in customer's orders")
    public PaymentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long orderId) {
        return paymentService.getOwn(CurrentUser.id(jwt), orderId);
    }
}
