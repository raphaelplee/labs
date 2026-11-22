package de.raphaellee.transflow.orchestration;

import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Component
class SagaClient {

    private static final Logger log = LoggerFactory.getLogger(SagaClient.class);
    private static final String WORKFLOW_ID_PREFIX = "saga-";

    private final WorkflowClient workflowClient;
    private final String taskQueue;
    private final int fulfillmentTimeoutSeconds;
    private final int paymentTimeoutSeconds;

    SagaClient(WorkflowClient workflowClient,
               @Value("${temporal.task-queue}") String taskQueue,
               @Value("${saga.fulfillment-timeout-seconds}") int fulfillmentTimeoutSeconds,
               @Value("${saga.payment-timeout-seconds}") int paymentTimeoutSeconds) {
        this.workflowClient = workflowClient;
        this.taskQueue = taskQueue;
        this.fulfillmentTimeoutSeconds = fulfillmentTimeoutSeconds;
        this.paymentTimeoutSeconds = paymentTimeoutSeconds;
    }

    static String workflowId(UUID subscriptionId) {
        return WORKFLOW_ID_PREFIX + subscriptionId;
    }

    static UUID subscriptionId(String workflowId) {
        return UUID.fromString(workflowId.substring(WORKFLOW_ID_PREFIX.length()));
    }

    void start(UUID orderId, UUID subscriptionId) {
        var saga = stub(orderId, subscriptionId);
        try {
            WorkflowClient.start(saga::run, orderId.toString(), subscriptionId,
                fulfillmentTimeoutSeconds, paymentTimeoutSeconds);
            log.info("Saga started — workflowId={}", workflowId(subscriptionId));
        } catch (WorkflowExecutionAlreadyStarted e) {
            log.info("Saga already exists — workflowId={}", workflowId(subscriptionId));
        }
    }

    void signal(UUID orderId, UUID subscriptionId, Consumer<SubscriptionSagaWorkflow> signal) {
        var saga = stub(orderId, subscriptionId);
        var request = workflowClient.newSignalWithStartRequest();
        request.add(saga::run, orderId.toString(), subscriptionId,
            fulfillmentTimeoutSeconds, paymentTimeoutSeconds);
        request.add(() -> signal.accept(saga));
        try {
            workflowClient.signalWithStart(request);
        } catch (WorkflowExecutionAlreadyStarted e) {
            log.warn("Saga already closed — signal discarded, workflowId={}", workflowId(subscriptionId));
        }
    }

    private SubscriptionSagaWorkflow stub(UUID orderId, UUID subscriptionId) {
        var options = WorkflowOptions.newBuilder()
            .setWorkflowId(workflowId(subscriptionId))
            .setTaskQueue(taskQueue)
            .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
            .setMemo(Map.of("orderId", orderId.toString()))
            .build();
        return workflowClient.newWorkflowStub(SubscriptionSagaWorkflow.class, options);
    }
}
