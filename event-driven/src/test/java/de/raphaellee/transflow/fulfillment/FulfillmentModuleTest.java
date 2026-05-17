package de.raphaellee.transflow.fulfillment;

import io.temporal.client.WorkflowClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ApplicationModuleTest
@Tag("unit")
@Transactional
class FulfillmentModuleTest {

    @Autowired
    FulfillmentService fulfillmentService;

    @MockBean
    WorkflowClient workflowClient;

    @Test
    void complete_savesFulfillmentRecord() {
        var orderId = java.util.UUID.randomUUID().toString();
        var result = fulfillmentService.complete(orderId, "sub-fulfill-1");

        assertThat(result.status()).isEqualTo("FULFILLED");
        assertThat(result.orderId().toString()).isEqualTo(orderId);
    }

    @Test
    void getByOrderId_notFound_throws() {
        assertThatThrownBy(() -> fulfillmentService.getByOrderId(java.util.UUID.randomUUID()))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
    }
}
