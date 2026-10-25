package de.raphaellee.transflow.integration;

import de.raphaellee.transflow.fulfillment.FulfillmentService;
import de.raphaellee.transflow.order.OrderService;
import de.raphaellee.transflow.payment.PaymentScenario;
import de.raphaellee.transflow.payment.PaymentService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
class SagaFlowIntegrationTest {

    static Network network = Network.newNetwork();

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.10");

    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.9.7"));

    @Container
    static PostgreSQLContainer<?> temporalDb = new PostgreSQLContainer<>("postgres:17.10")
        .withNetwork(network)
        .withNetworkAliases("temporal-postgres");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> temporal = new GenericContainer<>("temporalio/auto-setup:1.29.6.1")
        .withNetwork(network)
        .dependsOn(temporalDb)
        .withEnv("DB", "postgres12")
        .withEnv("DB_PORT", "5432")
        .withEnv("POSTGRES_USER", "test")
        .withEnv("POSTGRES_PWD", "test")
        .withEnv("POSTGRES_SEEDS", "temporal-postgres")
        .withEnv("BIND_ON_IP", "0.0.0.0")
        .withExposedPorts(7233)
        .waitingFor(Wait.forLogMessage(".*Temporal server started.*", 1)
            .withStartupTimeout(Duration.ofSeconds(120)));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.temporal.connection.target", () ->
            temporal.getHost() + ":" + temporal.getMappedPort(7233));
        registry.add("spring.temporal.start-workers", () -> "true");
    }

    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired FulfillmentService fulfillmentService;

    @Test
    void happyPath_sagaReachesCompleted() {
        UUID subId = UUID.randomUUID();
        var order = orderService.createOrder(subId);
        paymentService.confirmPayment(order.orderId(), PaymentScenario.HAPPY_PATH);

        await().atMost(60, SECONDS).ignoreExceptions().untilAsserted(() -> {
            var fulfillment = fulfillmentService.getByOrderId(order.orderId());
            assertThat(fulfillment.status()).isEqualTo("FULFILLED");
        });
    }
}
