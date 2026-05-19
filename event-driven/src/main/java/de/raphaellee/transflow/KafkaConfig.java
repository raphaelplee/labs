package de.raphaellee.transflow;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter;

/**
 * Kafka message converter for @KafkaListener methods.
 *
 * Spring Modulith publishes domain events as raw JSON bytes via ByteArraySerializer
 * (no __TypeId__ header). ByteArrayDeserializer passes the bytes through untouched;
 * ByteArrayJsonMessageConverter then deserializes JSON → the target type based on
 * the @KafkaListener method parameter, avoiding the need for type headers entirely.
 */
@Configuration
class KafkaConfig {

    @Bean
    ByteArrayJsonMessageConverter kafkaMessageConverter() {
        return new ByteArrayJsonMessageConverter();
    }
}
