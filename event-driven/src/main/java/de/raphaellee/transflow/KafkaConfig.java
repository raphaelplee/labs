package de.raphaellee.transflow;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;

@Configuration
class KafkaConfig {

    @Bean
    ByteArrayJacksonJsonMessageConverter kafkaMessageConverter() {
        return new ByteArrayJacksonJsonMessageConverter();
    }
}
