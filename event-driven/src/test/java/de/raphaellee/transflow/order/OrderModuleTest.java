package de.raphaellee.transflow.order;

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
class OrderModuleTest {

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

    // Temporal is in the orchestration module — mock it so standalone context starts cleanly
    @MockitoBean
    WorkflowClient workflowClient;

    @Autowired
    OrderService orderService;

    @Test
    void createOrder_persistsOrderAndReturnsIt() {
        UUID subId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        var order = orderService.createOrder(subId);

        assertThat(order.orderId()).isNotNull();
        assertThat(order.subscriptionId()).isEqualTo(subId);
        assertThat(order.status()).isEqualTo("CREATED");
    }

    @Test
    void createOrder_duplicateSubscriptionId_throws() {
        UUID subId = UUID.randomUUID();
        orderService.createOrder(subId);

        assertThatThrownBy(() -> orderService.createOrder(subId))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("active order already exists");
    }

    @Test
    void getOrder_notFound_throws() {
        assertThatThrownBy(() -> orderService.getOrder(UUID.randomUUID()))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }
}
