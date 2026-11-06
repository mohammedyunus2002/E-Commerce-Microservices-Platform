package com.ecommerce.order;

import com.ecommerce.messaging.OutboxEvent;
import com.ecommerce.messaging.OutboxEventRepository;
import com.ecommerce.order.domain.CustomerOrder;
import com.ecommerce.order.repository.CustomerOrderRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ConfigurationPropertiesScan
@EntityScan(basePackageClasses = {CustomerOrder.class, OutboxEvent.class})
@EnableJpaRepositories(basePackageClasses = {CustomerOrderRepository.class, OutboxEventRepository.class})
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
