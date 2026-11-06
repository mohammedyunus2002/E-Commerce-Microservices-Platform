package com.ecommerce.saga;

import com.ecommerce.inventory.InventoryServiceApplication;
import com.ecommerce.inventory.domain.InventoryItem;
import com.ecommerce.inventory.repository.InventoryItemRepository;
import com.ecommerce.notification.NotificationServiceApplication;
import com.ecommerce.notification.domain.Notification;
import com.ecommerce.notification.domain.NotificationType;
import com.ecommerce.notification.repository.NotificationRepository;
import com.ecommerce.order.OrderServiceApplication;
import com.ecommerce.order.domain.OrderStatus;
import com.ecommerce.order.dto.OrderLineRequest;
import com.ecommerce.order.repository.CustomerOrderRepository;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.payment.PaymentServiceApplication;
import com.ecommerce.payment.domain.PaymentStatus;
import com.ecommerce.payment.repository.PaymentRepository;
import com.sun.net.httpserver.HttpServer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots order, inventory, payment, and notification against one Kafka broker and checks the
 * saga paths that cross service boundaries.
 */
@Testcontainers(disabledWithoutDocker = true)
class SagaFlowTest {

    private static final String SECRET = "test-only-signing-key-0123456789-abcdef";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.1"));

    private static HttpServer catalog;
    private static ConfigurableApplicationContext orders;
    private static ConfigurableApplicationContext inventory;
    private static ConfigurableApplicationContext payments;
    private static ConfigurableApplicationContext notifications;

    @BeforeAll
    static void startPlatform() throws Exception {
        catalog = catalog();
        String brokers = KAFKA.getBootstrapServers();
        String catalogUrl = "http://127.0.0.1:" + catalog.getAddress().getPort();
        orders = start(OrderServiceApplication.class, "order-service", "order_saga", brokers, Map.of(
                "app.product-service.url", catalogUrl,
                "app.cart.cache-enabled", "false"));
        inventory = start(InventoryServiceApplication.class, "inventory-service", "inventory_saga", brokers, Map.of());
        payments = start(PaymentServiceApplication.class, "payment-service", "payment_saga", brokers, Map.of());
        notifications = start(NotificationServiceApplication.class, "notification-service", "notification_saga", brokers,
                Map.of());
        waitForListeners(orders, inventory, payments, notifications);
        Thread.sleep(1500);
    }

    @AfterAll
    static void stopPlatform() {
        for (ConfigurableApplicationContext context : new ConfigurableApplicationContext[] {
                notifications, payments, inventory, orders}) {
            if (context != null) {
                context.close();
            }
        }
        if (catalog != null) {
            catalog.stop(0);
        }
    }

    @Test
    void successfulOrderConfirmsAndCommitsStock() throws Exception {
        seed(1L, 10);
        long orderId = place(8L, 1L, 2, new BigDecimal("20.00"));

        awaitOrder(orderId, OrderStatus.CONFIRMED);
        awaitStock(1L, 8, 0);
        assertThat(payments.getBean(PaymentRepository.class).findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CAPTURED);
        awaitNotifications(8L, NotificationType.ORDER_CONFIRMED);
    }

    @Test
    void declinedPaymentReleasesStockAndFailsTheOrder() throws Exception {
        seed(2L, 5);
        long orderId = place(9L, 2L, 1, new BigDecimal("10.13"));

        awaitOrder(orderId, OrderStatus.FAILED);
        awaitStock(2L, 5, 0);
        assertThat(payments.getBean(PaymentRepository.class).findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);
        awaitNotifications(9L, NotificationType.PAYMENT_FAILED);
    }

    @Test
    void cancellingAConfirmedOrderRefundsAndRestocks() throws Exception {
        seed(3L, 4);
        long orderId = place(10L, 3L, 1, new BigDecimal("8.00"));
        awaitOrder(orderId, OrderStatus.CONFIRMED);

        orders.getBean(OrderService.class).cancel(10L, orderId);
        awaitOrder(orderId, OrderStatus.REFUNDED);
        awaitStock(3L, 4, 0);
        assertThat(payments.getBean(PaymentRepository.class).findByOrderId(orderId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        awaitNotifications(10L, NotificationType.ORDER_CANCELLED, NotificationType.PAYMENT_REFUNDED);
    }

    @Test
    void insufficientStockFailsTheOrderWithoutAPayment() throws Exception {
        seed(4L, 1);
        long orderId = place(11L, 4L, 2, new BigDecimal("4.00"));

        awaitOrder(orderId, OrderStatus.FAILED);
        assertThat(stock(4L).getAvailableQuantity()).isEqualTo(1);
        assertThat(payments.getBean(PaymentRepository.class).findByOrderId(orderId)).isEmpty();
    }

    @Test
    void invalidEventIsPublishedToTheDeadLetterTopic() throws Exception {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "saga-dlt-test");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(List.of("order-created-dlt"));
            consumer.poll(Duration.ofMillis(500));

            Properties producerProps = new Properties();
            producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
                producer.send(new ProducerRecord<>("order-created", "dead", "not-json")).get();
            }

            String value = null;
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (System.nanoTime() < deadline && value == null) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                if (!records.isEmpty()) {
                    value = records.iterator().next().value();
                }
            }
            assertThat(value).isEqualTo("not-json");
        }
    }

    private static long place(long userId, long productId, int quantity, BigDecimal expectedTotal) {
        var placed = orders.getBean(OrderService.class).place(userId, List.of(new OrderLineRequest(productId, quantity)));
        assertThat(placed.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(placed.totalAmount()).isEqualByComparingTo(expectedTotal);
        return placed.id();
    }

    private static void awaitOrder(long orderId, OrderStatus expected) throws InterruptedException {
        CustomerOrderRepository repository = orders.getBean(CustomerOrderRepository.class);
        OrderStatus seen = null;
        for (int i = 0; i < 100; i++) {
            seen = repository.findById(orderId).orElseThrow().getStatus();
            if (seen == expected) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Order " + orderId + " stayed " + seen + "; expected " + expected);
    }

    private static void seed(long productId, int available) {
        InventoryItemRepository items = inventory.getBean(InventoryItemRepository.class);
        items.findById(productId).ifPresent(existing -> items.delete(existing));
        items.saveAndFlush(InventoryItem.create(productId, available, Instant.now()));
    }

    private static void awaitStock(long productId, int available, int reserved) throws InterruptedException {
        InventoryItem seen = null;
        for (int i = 0; i < 100; i++) {
            seen = stock(productId);
            if (seen.getAvailableQuantity() == available && seen.getReservedQuantity() == reserved) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Product " + productId + " stayed available=" + seen.getAvailableQuantity()
                + " reserved=" + seen.getReservedQuantity()
                + "; expected available=" + available + " reserved=" + reserved);
    }

    private static void awaitNotifications(long userId, NotificationType... expected) throws InterruptedException {
        List<NotificationType> seen = List.of();
        List<NotificationType> wanted = List.of(expected);
        for (int i = 0; i < 100; i++) {
            seen = notificationTypes(userId);
            if (seen.containsAll(wanted)) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("User " + userId + " notifications stayed " + seen + "; expected " + wanted);
    }

    private static InventoryItem stock(long productId) {
        return inventory.getBean(InventoryItemRepository.class).findById(productId).orElseThrow();
    }

    private static List<NotificationType> notificationTypes(long userId) {
        return notifications.getBean(NotificationRepository.class).findAll().stream()
                .filter(notification -> notification.getUserId().equals(userId))
                .map(Notification::getType)
                .toList();
    }

    private static ConfigurableApplicationContext start(Class<?> application, String service, String database,
                                                        String brokers, Map<String, String> extra) {
        // Each service ships an application.yml. Loading every one from the shared test classpath
        // would merge ports, Flyway locations, and names, so each process reads only its own file.
        Path config = Path.of("..", service, "src", "main", "resources", "application.yml").toAbsolutePath();
        String jdbcUrl = "jdbc:h2:mem:" + database + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("spring.config.location", "file:" + config);
        properties.put("server.port", "0");
        properties.put("spring.jmx.enabled", "false");
        properties.put("spring.main.banner-mode", "off");
        properties.put("DB_URL", jdbcUrl);
        properties.put("DB_USERNAME", "sa");
        properties.put("DB_PASSWORD", "sa");
        properties.put("JWT_SECRET", SECRET);
        properties.put("KAFKA_BOOTSTRAP_SERVERS", brokers);
        properties.put("SAGA_OUTBOX_DELAY", "200ms");
        properties.put("SAGA_RETRY_INITIAL", "100ms");
        properties.put("SAGA_RETRY_MAX_ELAPSED", "2s");
        properties.putAll(extra);
        List<String> args = new ArrayList<>();
        properties.forEach((key, value) -> args.add("--" + key + "=" + value));
        return new SpringApplication(application).run(args.toArray(String[]::new));
    }

    private static void waitForListeners(ConfigurableApplicationContext... contexts) throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            boolean ready = true;
            for (ConfigurableApplicationContext context : contexts) {
                KafkaListenerEndpointRegistry registry = context.getBean(KafkaListenerEndpointRegistry.class);
                if (registry.getListenerContainers().isEmpty()
                        || registry.getListenerContainers().stream().anyMatch(container -> !container.isRunning())) {
                    ready = false;
                }
            }
            if (ready) {
                return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Saga listeners did not start");
    }

    private static HttpServer catalog() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/products/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String id = path.substring(path.lastIndexOf('/') + 1);
            String price = switch (id) {
                case "2" -> "10.13";
                case "4" -> "2.00";
                case "3" -> "8.00";
                default -> "10.00";
            };
            byte[] body = ("{\"id\":" + id + ",\"sku\":\"SKU-" + id + "\",\"name\":\"Item " + id
                    + "\",\"price\":" + price + ",\"active\":true}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return server;
    }
}
