package com.ecommerce.notification.web;

import com.ecommerce.common.web.PageResponse;
import com.ecommerce.notification.dto.NotificationResponse;
import com.ecommerce.notification.dto.RecordNotificationRequest;
import com.ecommerce.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@Tag(name = "Notification administration")
public class AdminNotificationController {

    private final NotificationService notificationService;

    public AdminNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    @Operation(summary = "Store an email record. This does not deliver mail")
    public ResponseEntity<NotificationResponse> record(@Valid @RequestBody RecordNotificationRequest request) {
        NotificationResponse created = notificationService.record(request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequestUri()
                        .path("/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping
    @Operation(summary = "Every stored email record, newest first")
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return notificationService.listAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }
}
