package de.raphaellee.transflow.orchestration;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
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

    /** Creates and starts a saga workflow stub asynchronously. Returns the stub for signalling and querying. */
    private SubscriptionSagaWorkflow startSaga(TestWorkflowEnvironment env, Worker worker, String workflowId) {
        var stub = env.getWorkflowClient()
            .newWorkflowStub(SubscriptionSagaWorkflow.class,
                WorkflowOptions.newBuilder()
                    .setTaskQueue(worker.getTaskQueue())
                    .setWorkflowId(workflowId)
                    .build());
        WorkflowClient.start(stub::run, "order-" + workflowId, UUID.randomUUID(), 30);
        return stub;
    }

    /** Blocks until the given workflow run completes. */
    private void awaitCompletion(TestWorkflowEnvironment env, String workflowId) {
        env.getWorkflowClient()
            .newUntypedWorkflowStub(workflowId)
            .getResult(Void.class);
    }

    @Test
    void paymentOk_advancesToFulfillmentProcessing(TestWorkflowEnvironment env, Worker worker) {
        var stub = startSaga(env, worker, "test-saga-1");
        stub.paymentOk();
        assertThat(stub.getStatus()).isEqualTo("FULFILLMENT_PROCESSING");
    }

    @Test
    void paymentFailed_reachesPaymentFailedEndState(TestWorkflowEnvironment env, Worker worker) {
        var stub = startSaga(env, worker, "test-saga-2");
        stub.paymentFailed();
        awaitCompletion(env, "test-saga-2");
        assertThat(stub.getStatus()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void paymentOkThenFulfillmentDone_reachesCompleted(TestWorkflowEnvironment env, Worker worker) {
        var stub = startSaga(env, worker, "test-saga-3");
        stub.paymentOk();
        stub.fulfillmentDone();
        awaitCompletion(env, "test-saga-3");
        assertThat(stub.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void paymentOkThenTimerFires_reachesTimedOut(TestWorkflowEnvironment env, Worker worker) {
        var stub = startSaga(env, worker, "test-saga-4");
        stub.paymentOk();
        env.sleep(Duration.ofSeconds(31));
        awaitCompletion(env, "test-saga-4");
        assertThat(stub.getStatus()).isEqualTo("TIMED_OUT");
    }
}
