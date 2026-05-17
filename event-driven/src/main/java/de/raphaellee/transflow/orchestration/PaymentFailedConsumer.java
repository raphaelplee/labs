package de.raphaellee.transflow.orchestration;

import de.raphaellee.transflow.payment.PaymentFailedEvent;
import io.temporal.client.WorkflowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class PaymentFailedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentFailedConsumer.class);

    private final WorkflowClient workflowClient;

    PaymentFailedConsumer(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @KafkaListener(topics = "payment.failed", groupId = "transflow-orchestration")
    void consume(PaymentFailedEvent event) {
        String workflowId = "saga-" + event.subscriptionId();
        log.info("Signalling PAYMENT_FAILED — workflowId={}", workflowId);

        workflowClient.newWorkflowStub(SubscriptionSagaWorkflow.class, workflowId)
            .paymentFailed();
    }
}
