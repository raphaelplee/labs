package de.raphaellee.transflow.payment;

import de.raphaellee.transflow.order.OrderService;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest(mode = ApplicationModuleTest.BootstrapMode.ALL_DEPENDENCIES)
@Tag("unit")
@Transactional
class PaymentModuleTest {

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
