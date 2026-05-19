package de.raphaellee.transflow;

import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;

/**
 * Explicit Kafka listener container factory.
 *
 * Spring Modulith publishes domain events as raw JSON bytes via ByteArraySerializer
 * (no __TypeId__ header). ByteArrayDeserializer passes the bytes through untouched;
 * ByteArrayJsonMessageConverter then deserializes JSON → the target type based on
 * the @KafkaListener method parameter, avoiding the need for type headers entirely.
 *
 * We configure the factory explicitly rather than registering ByteArrayJsonMessageConverter
 * as a bare @Bean. Spring Boot 4 auto-configuration detects any AbstractJsonMessageConverter
 * bean and overrides the consumer factory deserializer with JsonDeserializer — which then
 * fails because Spring Modulith produces byte[] payloads with no __TypeId__ headers.
 * Naming this bean "kafkaListenerContainerFactory" satisfies @ConditionalOnMissingBean
 * and prevents the auto-configured factory from running.
 */
@Configuration
class KafkaConfig {

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, byte[]> kafkaListenerContainerFactory(
            KafkaProperties kafkaProperties) {
        var consumerProps = kafkaProperties.buildConsumerProperties(null);
        var consumerFactory = new DefaultKafkaConsumerFactory<String, byte[]>(consumerProps);
        var factory = new ConcurrentKafkaListenerContainerFactory<String, byte[]>();
        factory.setConsumerFactory(consumerFactory);
        factory.setMessageConverter(new ByteArrayJsonMessageConverter());
        return factory;
    }
}
