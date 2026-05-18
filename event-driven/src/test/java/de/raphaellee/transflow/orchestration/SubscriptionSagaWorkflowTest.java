package de.raphaellee.transflow.orchestration;

import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
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
        .registerWorkflowImplementationTypes(SubscriptionSagaWorkflowImpl.class)
        .build();

    @Test
    void paymentOk_advancesToFulfillmentProcessing(SubscriptionSagaWorkflow workflow) {
        WorkflowClient.start(workflow::run, "order-1", UUID.randomUUID(), 30);
        workflow.paymentOk();
        assertThat(workflow.getStatus()).isEqualTo("FULFILLMENT_PROCESSING");
    }

    @Test
    void paymentFailed_reachesPaymentFailedEndState(SubscriptionSagaWorkflow workflow, TestWorkflowEnvironment env) {
        var exec = WorkflowClient.start(workflow::run, "order-1", UUID.randomUUID(), 30);
        workflow.paymentFailed();
        env.getWorkflowClient().newUntypedWorkflowStub(exec.getWorkflowId()).getResult(Void.class);
        assertThat(workflow.getStatus()).isEqualTo("PAYMENT_FAILED");
    }

    @Test
    void paymentOkThenFulfillmentDone_reachesCompleted(SubscriptionSagaWorkflow workflow, TestWorkflowEnvironment env) {
        var exec = WorkflowClient.start(workflow::run, "order-1", UUID.randomUUID(), 30);
        workflow.paymentOk();
        workflow.fulfillmentDone();
        env.getWorkflowClient().newUntypedWorkflowStub(exec.getWorkflowId()).getResult(Void.class);
        assertThat(workflow.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void paymentOkThenTimerFires_reachesTimedOut(SubscriptionSagaWorkflow workflow, TestWorkflowEnvironment env) {
        var exec = WorkflowClient.start(workflow::run, "order-1", UUID.randomUUID(), 30);
        workflow.paymentOk();
        env.sleep(Duration.ofSeconds(31));
        env.getWorkflowClient().newUntypedWorkflowStub(exec.getWorkflowId()).getResult(Void.class);
        assertThat(workflow.getStatus()).isEqualTo("TIMED_OUT");
    }
}
