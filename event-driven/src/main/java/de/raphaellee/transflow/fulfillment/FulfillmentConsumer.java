package de.raphaellee.transflow.fulfillment;

import de.raphaellee.transflow.payment.PaymentProcessedEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class FulfillmentConsumer {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentConsumer.class);

    private final FulfillmentService fulfillmentService;
    private final WorkflowClient workflowClient;

    FulfillmentConsumer(FulfillmentService fulfillmentService, WorkflowClient workflowClient) {
        this.fulfillmentService = fulfillmentService;
        this.workflowClient = workflowClient;
    }

    @KafkaListener(topics = "payment.processed", groupId = "transflow-fulfillment")
    void consume(PaymentProcessedEvent event) throws InterruptedException {
        String workflowId = "saga-" + event.subscriptionId();
        log.info("Fulfillment starting — workflowId={} scenario={}", workflowId, event.scenario());

        if ("fulfillment-timeout".equals(event.scenario())) {
            log.info("Simulating slow fulfillment — sleeping 35s to trigger workflow timeout");
            Thread.sleep(35_000);
        }

        fulfillmentService.complete(event.orderId(), event.subscriptionId());

        try {
            workflowClient.newUntypedWorkflowStub(workflowId).signal("fulfillmentDone");
            log.info("FULFILLMENT_DONE signal sent — workflowId={}", workflowId);
        } catch (WorkflowNotFoundException e) {
            log.warn("Workflow {} already closed — FULFILLMENT_DONE signal discarded", workflowId);
        }
    }
}
