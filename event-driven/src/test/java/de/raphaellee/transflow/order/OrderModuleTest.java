package de.raphaellee.transflow.order;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest
@Tag("unit")
@Transactional
class OrderModuleTest {

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
        assertThatThrownBy(() -> orderService.getOrder(java.util.UUID.randomUUID()))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }
}
