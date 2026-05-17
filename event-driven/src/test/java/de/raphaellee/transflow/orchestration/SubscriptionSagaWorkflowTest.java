package de.raphaellee.transflow.orchestration;

import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class SubscriptionSagaWorkflowTest {

    @RegisterExtension
    static final TestWorkflowExtension testWorkflow = TestWorkflowExtension.newBuilder()
        .setWorkflowTypes(SubscriptionSagaWorkflowImpl.class)
        .setDoNotStart(false)
        .build();

    @Test
    void paymentOk_advancesToFulfillmentProcessing(TestWorkflowEnvironment env, Worker worker,
                                                    SubscriptionSagaWorkflow workflow) {
        // Start workflow async
        var stub = env.getWorkflowClient()
            .newWorkflowStub(SubscriptionSagaWorkflow.class,
                io.temporal.client.WorkflowOptions.newBuilder()
                    .setTaskQueue(worker.getTaskQueue())
                    .setWorkflowId("test-saga-1")
                    .build());

        io.temporal.client.WorkflowClient.start(stub::run, "order-1", UUID.fromString("00000000-0000-0000-0000-000000000001"), 30);

        // Signal PAYMENT_OK
        stub.paymentOk();

        // Query status — should be FULFILLMENT_PROCESSING
        assertThat(stub.getStatus()).isEqualTo("FULFILLMENT_PROCESSING");
    }

    @Test
    void paymentFailed_reachesPaymentFailedEndState(TestWorkflowEnvironment env, Worker worker) {
        var stub = env.getWorkflowClient()
            .newWorkflowStub(SubscriptionSagaWorkflow.class,
                io.temporal.client.WorkflowOptions.newBuilder()
                    .setTaskQueue(worker.getTaskQueue())
                    .setWorkflowId("test-saga-2")
                    .build());

        io.temporal.client.WorkflowClient.start(stub::run, "order-2", UUID.fromString("00000000-0000-0000-0000-000000000002"), 30);
        stub.paymentFailed();

        // Workflow should complete with PAYMENT_FAILED
        env.getWorkflowClient()
            .newUntypedWorkflowStub("test-saga-2")
            .getResult(String.class); // blocks until workflow completes

        assertThat(stub.getStatus()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void paymentOkThenFulfillmentDone_reachesCompleted(TestWorkflowEnvironment env, Worker worker) {
        var stub = env.getWorkflowClient()
            .newWorkflowStub(SubscriptionSagaWorkflow.class,
                io.temporal.client.WorkflowOptions.newBuilder()
                    .setTaskQueue(worker.getTaskQueue())
                    .setWorkflowId("test-saga-3")
                    .build());

        io.temporal.client.WorkflowClient.start(stub::run, "order-3", UUID.fromString("00000000-0000-0000-0000-000000000003"), 30);
        stub.paymentOk();
        stub.fulfillmentDone();

        env.getWorkflowClient()
            .newUntypedWorkflowStub("test-saga-3")
            .getResult(String.class);

        assertThat(stub.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void paymentOkThenTimerFires_reachesTimedOut(TestWorkflowEnvironment env, Worker worker) {
        var stub = env.getWorkflowClient()
            .newWorkflowStub(SubscriptionSagaWorkflow.class,
                io.temporal.client.WorkflowOptions.newBuilder()
                    .setTaskQueue(worker.getTaskQueue())
                    .setWorkflowId("test-saga-4")
                    .build());

        io.temporal.client.WorkflowClient.start(stub::run, "order-4", UUID.fromString("00000000-0000-0000-0000-000000000004"), 30);
        stub.paymentOk();

        // Skip time past the 30s fulfillment timeout
        env.sleep(Duration.ofSeconds(31));

        env.getWorkflowClient()
            .newUntypedWorkflowStub("test-saga-4")
            .getResult(String.class);

        assertThat(stub.getStatus()).isEqualTo("TIMED_OUT");
    }
}
