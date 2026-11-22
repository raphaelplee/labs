package de.raphaellee.transflow;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;

@Configuration
class KafkaConfig {

    @Bean
    ByteArrayJacksonJsonMessageConverter kafkaMessageConverter() {
        return new ByteArrayJacksonJsonMessageConverter();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<?, ?> kafkaOperations) {
        var backOff = new ExponentialBackOffWithMaxRetries(5);
        backOff.setInitialInterval(500);
        backOff.setMultiplier(2);
        backOff.setMaxInterval(10_000);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaOperations), backOff);
    }
}
