package com.ecommerce.notification.dto;

import com.ecommerce.notification.domain.Notification;
import com.ecommerce.notification.domain.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long userId,
        Long orderId,
        NotificationType type,
        String subject,
        String body,
        Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getUserId(), notification.getOrderId(),
                notification.getType(), notification.getSubject(), notification.getBody(),
                notification.getCreatedAt());
    }
}
