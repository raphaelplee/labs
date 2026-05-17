package de.raphaellee.transflow.fulfillment;

import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest
@Testcontainers
@Tag("integration")
@Transactional
class FulfillmentModuleTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.10");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    FulfillmentService fulfillmentService;

    @MockitoBean
    WorkflowClient workflowClient;

    @Test
    void complete_savesFulfillmentRecord() {
        var orderId = UUID.randomUUID().toString();
        var result = fulfillmentService.complete(orderId, UUID.randomUUID());

        assertThat(result.status()).isEqualTo("FULFILLED");
        assertThat(result.orderId().toString()).isEqualTo(orderId);
    }

    @Test
    void getByOrderId_notFound_throws() {
        assertThatThrownBy(() -> fulfillmentService.getByOrderId(UUID.randomUUID()))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }
}
