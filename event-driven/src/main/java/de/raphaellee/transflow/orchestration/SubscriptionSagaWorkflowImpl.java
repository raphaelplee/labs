package de.raphaellee.transflow.orchestration;

import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.UUID;

public class SubscriptionSagaWorkflowImpl implements SubscriptionSagaWorkflow {

    private static final Logger log = Workflow.getLogger(SubscriptionSagaWorkflowImpl.class);

    private boolean paymentOk = false;
    private boolean paymentFailed = false;
    private boolean fulfillmentDone = false;
    private String status = "AWAITING_PAYMENT";

    @Override
    public void run(String orderId, UUID subscriptionId, int fulfillmentTimeoutSeconds) {
        log.info("Saga started — orderId={} subscriptionId={}", orderId, subscriptionId);

        Workflow.await(() -> paymentOk || paymentFailed);

        if (paymentFailed) {
            status = "PAYMENT_FAILED";
            log.info("Saga ended with PAYMENT_FAILED — orderId={}", orderId);
            return;
        }

        status = "FULFILLMENT_PROCESSING";
        log.info("Payment confirmed — awaiting fulfillment for orderId={}", orderId);

        boolean completed = Workflow.await(
            Duration.ofSeconds(fulfillmentTimeoutSeconds), () -> fulfillmentDone);

        if (!completed) {
            status = "TIMED_OUT";
            log.warn("Fulfillment timed out after {}s — orderId={}", fulfillmentTimeoutSeconds, orderId);
            return;
        }

        status = "COMPLETED";
        log.info("Saga COMPLETED — orderId={}", orderId);
    }

    @Override
    public void paymentOk() {
        this.paymentOk = true;
    }

    @Override
    public void paymentFailed() {
        this.paymentFailed = true;
    }

    @Override
    public void fulfillmentDone() {
        this.fulfillmentDone = true;
    }

    @Override
    public String getStatus() {
        return status;
    }
}
