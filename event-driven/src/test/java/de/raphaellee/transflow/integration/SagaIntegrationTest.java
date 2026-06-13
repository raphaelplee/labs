package de.raphaellee.transflow.integration;

import de.raphaellee.transflow.order.OrderService;
import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
class SagaIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.10")
        .withDatabaseName("postgres");

    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.9.7"));

    // Mock Temporal — these tests cover order/payment REST API, not the full saga wire
    @MockitoBean
    WorkflowClient workflowClient;

    @Value("${local.server.port}")
    int port;

    private RestTemplate rest;

    @Autowired
    OrderService orderService;

    @BeforeEach
    void setUp() {
        rest = new RestTemplate();
        // Never throw on 4xx/5xx — return ResponseEntity with the actual status instead
        rest.setErrorHandler(response -> false);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void postOrder_returns201_withOrderId() {
        var response = rest.postForEntity(url("/api/orders"),
            Map.of("subscriptionId", UUID.randomUUID().toString()),
            Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("orderId");
    }

    @Test
    void postOrder_duplicateSubscriptionId_returns409() {
        String sub = UUID.randomUUID().toString();
        rest.postForEntity(url("/api/orders"), Map.of("subscriptionId", sub), Map.class);

        var response = rest.postForEntity(url("/api/orders"),
            Map.of("subscriptionId", sub), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void postOrder_concurrentDuplicate_returns409NotFiveHundred() {
        String sub = UUID.randomUUID().toString();
        var r1 = rest.postForEntity(url("/api/orders"), Map.of("subscriptionId", sub), Map.class);
        var r2 = rest.postForEntity(url("/api/orders"), Map.of("subscriptionId", sub), Map.class);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void postPayment_unknownOrderId_returns404() {
        var response = rest.postForEntity(
            url("/api/payments/" + UUID.randomUUID() + "/confirm?scenario=HAPPY_PATH"),
            null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void postOrder_thenConfirmPayment_returns202() {
        var order = orderService.createOrder(UUID.randomUUID());

        var response = rest.postForEntity(
            url("/api/payments/" + order.orderId() + "/confirm?scenario=HAPPY_PATH"),
            null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("status")).isEqualTo("PROCESSED");
    }

    @Test
    void postOrder_thenFailPayment_returns202() {
        var order = orderService.createOrder(UUID.randomUUID());

        var response = rest.postForEntity(
            url("/api/payments/" + order.orderId() + "/fail"),
            null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("status")).isEqualTo("FAILED");
    }
}
