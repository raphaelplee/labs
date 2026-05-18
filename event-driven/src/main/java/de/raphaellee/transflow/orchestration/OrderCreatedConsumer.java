package de.raphaellee.transflow.orchestration;

import de.raphaellee.transflow.order.OrderCreatedEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class OrderCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final WorkflowClient workflowClient;

    @Value("${temporal.task-queue:subscription-saga-queue}")
    private String taskQueue;

    @Value("${saga.fulfillment-timeout-seconds:30}")
    private int fulfillmentTimeoutSeconds;

    OrderCreatedConsumer(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @KafkaListener(topics = "order.created", groupId = "transflow-orchestration")
    void consume(OrderCreatedEvent event) {
        String workflowId = "saga-" + event.subscriptionId().toString();
        log.info("Starting saga — workflowId={} orderId={}", workflowId, event.orderId());

        var options = WorkflowOptions.newBuilder()
            .setWorkflowId(workflowId)
            .setTaskQueue(taskQueue)
            .build();

        var workflow = workflowClient.newWorkflowStub(SubscriptionSagaWorkflow.class, options);

        try {
            WorkflowClient.start(workflow::run, event.orderId().toString(), event.subscriptionId(), fulfillmentTimeoutSeconds);
        } catch (WorkflowExecutionAlreadyStarted e) {
            log.info("Saga already running for workflowId={} — idempotent, skipping", workflowId);
        }
    }
}
