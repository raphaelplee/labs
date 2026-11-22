package de.raphaellee.transflow.integration;

import de.raphaellee.transflow.TestcontainersConfiguration;
import de.raphaellee.transflow.fulfillment.FulfillmentService;
import de.raphaellee.transflow.order.OrderService;
import de.raphaellee.transflow.payment.PaymentScenario;
import de.raphaellee.transflow.payment.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "saga.fulfillment-timeout-seconds=" + SagaFlowIntegrationTest.FULFILLMENT_TIMEOUT_SECONDS)
@Import(TestcontainersConfiguration.class)
@Testcontainers
@Tag("integration")
class SagaFlowIntegrationTest {

    static final int FULFILLMENT_TIMEOUT_SECONDS = 10;

    static Network network = Network.newNetwork();

    @Container
    static PostgreSQLContainer temporalDb = new PostgreSQLContainer("postgres:17.10")
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

    @Value("${local.server.port}")
    int port;

    private RestTemplate rest;

    @BeforeEach
    void setUp() {
        rest = new RestTemplate();
        rest.setErrorHandler(response -> false);
    }

    @Test
    void happyPath_sagaReachesCompleted() {
        UUID subId = UUID.randomUUID();
        var order = orderService.createOrder(subId);
        paymentService.confirmPayment(order.orderId(), PaymentScenario.HAPPY_PATH);

        awaitSagaStatus(subId, "COMPLETED");
        assertThat(fulfillmentService.getByOrderId(order.orderId()).status()).isEqualTo("FULFILLED");
    }

    @Test
    void failedPayment_sagaReachesPaymentFailed() {
        UUID subId = UUID.randomUUID();
        var order = orderService.createOrder(subId);
        paymentService.failPayment(order.orderId());

        awaitSagaStatus(subId, "PAYMENT_FAILED");
    }

    @Test
    void slowFulfillment_sagaTimesOutAndTheLateFulfillmentIsKept() {
        UUID subId = UUID.randomUUID();
        var order = orderService.createOrder(subId);
        paymentService.confirmPayment(order.orderId(), PaymentScenario.FULFILLMENT_TIMEOUT);

        awaitSagaStatus(subId, "TIMED_OUT");
        await().atMost(FULFILLMENT_TIMEOUT_SECONDS + 30, SECONDS).ignoreExceptions().untilAsserted(() ->
            assertThat(fulfillmentService.getByOrderId(order.orderId()).status()).isEqualTo("FULFILLED"));
        assertThat(sagaStatus(subId)).isEqualTo("TIMED_OUT");
    }

    @Test
    void unknownSaga_returns404() {
        var response = rest.getForEntity(url("/api/sagas/saga-" + UUID.randomUUID()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void awaitSagaStatus(UUID subscriptionId, String expected) {
        await().atMost(60, SECONDS).ignoreExceptions().untilAsserted(() ->
            assertThat(sagaStatus(subscriptionId)).isEqualTo(expected));
    }

    private String sagaStatus(UUID subscriptionId) {
        var response = rest.getForEntity(url("/api/sagas/saga-" + subscriptionId), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("status");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
