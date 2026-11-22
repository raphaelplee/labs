package de.raphaellee.transflow.order;

import de.raphaellee.transflow.ConflictException;
import de.raphaellee.transflow.TestcontainersConfiguration;
import io.temporal.client.WorkflowClient;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
@Tag("integration")
@Transactional
class OrderModuleTest {

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
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void getOrder_notFound_throws() {
        assertThatThrownBy(() -> orderService.getOrder(UUID.randomUUID()))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
