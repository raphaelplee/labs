package de.raphaellee.transflow.payment;

import de.raphaellee.transflow.order.OrderService;
import io.temporal.client.WorkflowClient;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@Testcontainers
@Tag("integration")
@Transactional
class PaymentModuleTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.10");

    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.9.7"));

    // Temporal is in the orchestration module — mock it so context starts cleanly
    @MockitoBean
    WorkflowClient workflowClient;

    @Autowired
    PaymentService paymentService;

    @Autowired
    OrderService orderService;

    @Test
    void confirmPayment_createsPaymentRecord() {
        var order = orderService.createOrder(UUID.randomUUID());

        var payment = paymentService.confirmPayment(order.orderId(), PaymentScenario.HAPPY_PATH);

        assertThat(payment.orderId()).isEqualTo(order.orderId());
        assertThat(payment.status()).isEqualTo("PROCESSED");
    }

    @Test
    void failPayment_createsFailedRecord() {
        var order = orderService.createOrder(UUID.randomUUID());

        var payment = paymentService.failPayment(order.orderId());

        assertThat(payment.orderId()).isEqualTo(order.orderId());
        assertThat(payment.status()).isEqualTo("FAILED");
    }

    @Test
    void confirmPayment_unknownOrder_throws() {
        assertThatThrownBy(() -> paymentService.confirmPayment(UUID.randomUUID(), PaymentScenario.HAPPY_PATH))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }
}
