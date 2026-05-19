package de.raphaellee.transflow;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;

/**
 * Kafka message converter for @KafkaListener methods.
 *
 * Spring Modulith publishes domain events as raw JSON bytes via ByteArraySerializer
 * (no __TypeId__ header). ByteArrayDeserializer (set in application.yml) passes the
 * bytes through untouched; ByteArrayJacksonJsonMessageConverter then deserializes
 * JSON → the target type based on the @KafkaListener method parameter, avoiding
 * the need for __TypeId__ headers entirely.
 *
 * Spring Boot 4 picks up a single RecordMessageConverter bean via
 * ObjectProvider.getIfUnique() and wires it to the auto-configured
 * KafkaListenerContainerFactory — no explicit factory configuration needed.
 */
@Configuration
class KafkaConfig {

    @Bean
    ByteArrayJacksonJsonMessageConverter kafkaMessageConverter() {
        return new ByteArrayJacksonJsonMessageConverter();
    }
}
