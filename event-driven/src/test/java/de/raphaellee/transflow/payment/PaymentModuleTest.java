package de.raphaellee.transflow.payment;

import de.raphaellee.transflow.ConflictException;
import de.raphaellee.transflow.TestcontainersConfiguration;
import de.raphaellee.transflow.order.OrderService;
import io.temporal.client.WorkflowClient;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.PublishedEvents;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
@Tag("integration")
@Transactional
class PaymentModuleTest {

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
    void confirmPaymentTwice_returnsTheSamePaymentAndPublishesOnce(PublishedEvents events) {
        var order = orderService.createOrder(UUID.randomUUID());

        var first = paymentService.confirmPayment(order.orderId(), PaymentScenario.HAPPY_PATH);
        var second = paymentService.confirmPayment(order.orderId(), PaymentScenario.HAPPY_PATH);

        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(events.ofType(PaymentProcessedEvent.class)).hasSize(1);
    }

    @Test
    void failAfterConfirm_isRejected(PublishedEvents events) {
        var order = orderService.createOrder(UUID.randomUUID());
        paymentService.confirmPayment(order.orderId(), PaymentScenario.HAPPY_PATH);

        assertThatThrownBy(() -> paymentService.failPayment(order.orderId()))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already PROCESSED");
        assertThat(events.ofType(PaymentFailedEvent.class)).isEmpty();
    }

    @Test
    void confirmPayment_unknownOrder_throws() {
        assertThatThrownBy(() -> paymentService.confirmPayment(UUID.randomUUID(), PaymentScenario.HAPPY_PATH))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
