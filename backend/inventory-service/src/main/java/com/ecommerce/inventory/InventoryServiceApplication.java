package com.ecommerce.inventory;

import com.ecommerce.inventory.domain.InventoryItem;
import com.ecommerce.inventory.repository.InventoryItemRepository;
import com.ecommerce.messaging.OutboxEvent;
import com.ecommerce.messaging.OutboxEventRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ConfigurationPropertiesScan
@EntityScan(basePackageClasses = {InventoryItem.class, OutboxEvent.class})
@EnableJpaRepositories(basePackageClasses = {InventoryItemRepository.class, OutboxEventRepository.class})
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
