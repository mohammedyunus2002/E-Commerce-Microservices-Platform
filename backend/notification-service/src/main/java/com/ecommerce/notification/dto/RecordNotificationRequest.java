package com.ecommerce.notification.dto;

import com.ecommerce.notification.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordNotificationRequest(
        @NotNull Long userId,
        Long orderId,
        @NotNull NotificationType type,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 2000) String body) {
}
