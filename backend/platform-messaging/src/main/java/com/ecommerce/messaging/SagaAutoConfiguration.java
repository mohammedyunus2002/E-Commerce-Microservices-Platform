package com.ecommerce.messaging;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(SagaProperties.class)
@ConditionalOnProperty(prefix = "app.saga", name = "enabled", havingValue = "true")
public class SagaAutoConfiguration {

    @Bean
    public SagaJson sagaJson(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new SagaJson(objectMapper);
    }

    @Bean
    public OutboxWriter outboxWriter(OutboxEventRepository repository, SagaJson sagaJson, Clock clock) {
        return new OutboxWriter(repository, sagaJson, clock);
    }

    @Bean
    public Idempotency idempotency(ProcessedEventRepository repository, Clock clock) {
        return new Idempotency(repository, clock);
    }

    @Bean
    public OutboxRelay outboxRelay(OutboxEventRepository repository, KafkaTemplate<?, ?> kafkaTemplate,
                                   PlatformTransactionManager transactionManager, Clock clock) {
        return new OutboxRelay(repository, kafkaTemplate, transactionManager, clock);
    }

    @Bean
    public SagaConsumer sagaConsumer(SagaJson sagaJson) {
        return new SagaConsumer(sagaJson);
    }

    @Bean
    public ConsumerFactory<String, String> sagaConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getConsumer().getGroupId());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public DefaultErrorHandler sagaErrorHandler(KafkaTemplate<?, ?> kafkaTemplate, SagaProperties properties) {
        ExponentialBackOff backOff = new ExponentialBackOff(properties.getRetryInitial().toMillis(), 2.0);
        backOff.setMaxElapsedTime(properties.getRetryMaxElapsed().toMillis());
        // Spring Kafka 3.3 names the dead-letter topic "<topic>-dlt".
        DefaultErrorHandler handler = new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> sagaListenerFactory(
            ConsumerFactory<String, String> sagaConsumerFactory, DefaultErrorHandler sagaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sagaConsumerFactory);
        factory.setCommonErrorHandler(sagaErrorHandler);
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    /** Reads one message, puts the order id on the log context, and hands the event to the service. */
    public static final class SagaConsumer {
        private final SagaJson sagaJson;

        public SagaConsumer(SagaJson sagaJson) {
            this.sagaJson = sagaJson;
        }

        public void consume(String json, Consumer<SagaEvent> handler) {
            SagaEvent event = sagaJson.readEvent(json);
            MDC.put("correlationId", event.correlationId());
            try {
                handler.accept(event);
            } finally {
                MDC.remove("correlationId");
            }
        }

        public SagaJson json() {
            return sagaJson;
        }
    }
}
