package de.raphaellee.transflow.orchestration;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.UUID;

@WorkflowInterface
public interface SubscriptionSagaWorkflow {

    @WorkflowMethod
    void run(String orderId, UUID subscriptionId, int fulfillmentTimeoutSeconds);

    @SignalMethod
    void paymentOk();

    @SignalMethod
    void paymentFailed();

    @SignalMethod
    void fulfillmentDone();

    @QueryMethod
    String getStatus();
}
