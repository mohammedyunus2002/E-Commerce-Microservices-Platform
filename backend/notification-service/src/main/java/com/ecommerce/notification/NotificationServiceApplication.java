package com.ecommerce.notification;

import com.ecommerce.messaging.OutboxEvent;
import com.ecommerce.messaging.OutboxEventRepository;
import com.ecommerce.notification.domain.Notification;
import com.ecommerce.notification.repository.NotificationRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackageClasses = {Notification.class, OutboxEvent.class})
@EnableJpaRepositories(basePackageClasses = {NotificationRepository.class, OutboxEventRepository.class})
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
