package com.ecommerce.notification.service;

import com.ecommerce.common.web.PageResponse;
import com.ecommerce.notification.domain.Notification;
import com.ecommerce.notification.dto.NotificationResponse;
import com.ecommerce.notification.dto.RecordNotificationRequest;
import com.ecommerce.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional
    public NotificationResponse record(RecordNotificationRequest request) {
        Notification saved = notificationRepository.saveAndFlush(new Notification(request.userId(), request.orderId(),
                request.type(), request.subject().trim(), request.body().trim(), clock.instant()));
        log.info("Recorded {} notification {} for user {} order {}", saved.getType(), saved.getId(),
                saved.getUserId(), saved.getOrderId());
        return NotificationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listOwn(long userId, Pageable pageable) {
        return PageResponse.from(notificationRepository.findByUserId(userId, pageable), NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> listAll(Pageable pageable) {
        return PageResponse.from(notificationRepository.findAll(pageable), NotificationResponse::from);
    }
}
