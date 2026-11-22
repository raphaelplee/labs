package de.raphaellee.transflow.fulfillment;

import de.raphaellee.transflow.TestcontainersConfiguration;
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

@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
@Tag("integration")
@Transactional
class FulfillmentModuleTest {

    @Autowired
    FulfillmentService fulfillmentService;

    @MockitoBean
    WorkflowClient workflowClient;

    @Test
    void complete_savesFulfillmentRecord() {
        var orderId = UUID.randomUUID();
        var result = fulfillmentService.complete(orderId, UUID.randomUUID());

        assertThat(result.status()).isEqualTo("FULFILLED");
        assertThat(result.orderId()).isEqualTo(orderId);
    }

    @Test
    void completeTwice_returnsTheSameRecordAndPublishesOnce(PublishedEvents events) {
        var orderId = UUID.randomUUID();
        var subscriptionId = UUID.randomUUID();

        var first = fulfillmentService.complete(orderId, subscriptionId);
        var second = fulfillmentService.complete(orderId, subscriptionId);

        assertThat(second.fulfillmentId()).isEqualTo(first.fulfillmentId());
        assertThat(fulfillmentService.getByOrderId(orderId).fulfillmentId()).isEqualTo(first.fulfillmentId());
        assertThat(events.ofType(FulfillmentCompletedEvent.class)).hasSize(1);
    }

    @Test
    void getByOrderId_notFound_throws() {
        assertThatThrownBy(() -> fulfillmentService.getByOrderId(UUID.randomUUID()))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
