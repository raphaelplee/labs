package de.raphaellee.transflow.order;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest
@Tag("unit")
class OrderModuleTest {

    @Autowired
    OrderService orderService;

    @Test
    void createOrder_persistsOrderAndReturnsIt() {
        var order = orderService.createOrder("sub-123");

        assertThat(order.orderId()).isNotNull();
        assertThat(order.subscriptionId()).isEqualTo("sub-123");
        assertThat(order.status()).isEqualTo("CREATED");
    }

    @Test
    void createOrder_duplicateSubscriptionId_throws() {
        orderService.createOrder("sub-dup");

        assertThatThrownBy(() -> orderService.createOrder("sub-dup"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("sub-dup");
    }

    @Test
    void getOrder_notFound_throws() {
        assertThatThrownBy(() -> orderService.getOrder(java.util.UUID.randomUUID()))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }
}
