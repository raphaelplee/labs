package de.raphaellee.transflow.orchestration;

import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class SagaClientTest {

    @RegisterExtension
    static final TestWorkflowExtension testWorkflow = TestWorkflowExtension.newBuilder()
        .registerWorkflowImplementationTypes(SubscriptionSagaWorkflowImpl.class)
        .build();

    private final UUID orderId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();

    @Test
    void paymentBeforeOrderCreated_startsTheSagaAndKeepsTheSignal(WorkflowClient client, Worker worker) {
        var sagaClient = sagaClient(client, worker);

        sagaClient.signal(orderId, subscriptionId, SubscriptionSagaWorkflow::paymentOk);
        sagaClient.start(orderId, subscriptionId);

        assertThat(status(client)).isEqualTo("FULFILLMENT_PROCESSING");
    }

    @Test
    void orderCreatedAfterTheSagaClosed_doesNotStartASecondRun(WorkflowClient client, Worker worker) {
        var sagaClient = sagaClient(client, worker);
        sagaClient.start(orderId, subscriptionId);
        sagaClient.signal(orderId, subscriptionId, SubscriptionSagaWorkflow::paymentFailed);
        awaitClose(client);

        sagaClient.start(orderId, subscriptionId);

        assertThat(executionStatus(client)).isEqualTo(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED);
        assertThat(status(client)).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void signalAfterTheSagaClosed_isDiscarded(WorkflowClient client, Worker worker) {
        var sagaClient = sagaClient(client, worker);
        sagaClient.start(orderId, subscriptionId);
        sagaClient.signal(orderId, subscriptionId, SubscriptionSagaWorkflow::paymentFailed);
        awaitClose(client);

        sagaClient.signal(orderId, subscriptionId, SubscriptionSagaWorkflow::fulfillmentDone);

        assertThat(executionStatus(client)).isEqualTo(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_COMPLETED);
        assertThat(status(client)).isEqualTo("PAYMENT_FAILED");
    }

    private SagaClient sagaClient(WorkflowClient client, Worker worker) {
        return new SagaClient(client, worker.getTaskQueue(), 30, 600);
    }

    private String status(WorkflowClient client) {
        return client.newWorkflowStub(SubscriptionSagaWorkflow.class, SagaClient.workflowId(subscriptionId)).getStatus();
    }

    private void awaitClose(WorkflowClient client) {
        client.newUntypedWorkflowStub(SagaClient.workflowId(subscriptionId)).getResult(Void.class);
    }

    private WorkflowExecutionStatus executionStatus(WorkflowClient client) {
        return client.newUntypedWorkflowStub(SagaClient.workflowId(subscriptionId)).describe().getStatus();
    }
}
