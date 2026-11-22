package de.raphaellee.transflow.integration;

import de.raphaellee.transflow.TestcontainersConfiguration;
import de.raphaellee.transflow.order.OrderService;
import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@Tag("integration")
class SagaIntegrationTest {

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
        var body = Map.of("subscriptionId", UUID.randomUUID().toString());
        var gate = new CountDownLatch(1);
        var requests = Stream.generate(() -> CompletableFuture.supplyAsync(() -> {
                awaitQuietly(gate);
                return rest.postForEntity(url("/api/orders"), body, Map.class).getStatusCode().value();
            }))
            .limit(2)
            .toList();
        gate.countDown();

        var statuses = requests.stream().map(CompletableFuture::join).sorted().toList();

        assertThat(statuses).containsExactly(201, 409);
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

    @Test
    void confirmThenFail_returns409() {
        var order = orderService.createOrder(UUID.randomUUID());
        rest.postForEntity(url("/api/payments/" + order.orderId() + "/confirm"), null, Map.class);

        var response = rest.postForEntity(url("/api/payments/" + order.orderId() + "/fail"), null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private static void awaitQuietly(CountDownLatch gate) {
        try {
            gate.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
