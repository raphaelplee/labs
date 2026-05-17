package de.raphaellee.transflow.orchestration;

import de.raphaellee.transflow.payment.PaymentProcessedEvent;
import io.temporal.client.WorkflowClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class PaymentProcessedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessedConsumer.class);

    private final WorkflowClient workflowClient;

    PaymentProcessedConsumer(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @KafkaListener(topics = "payment.processed", groupId = "transflow-orchestration")
    void consume(PaymentProcessedEvent event) {
        String workflowId = "saga-" + event.subscriptionId();
        log.info("Signalling PAYMENT_OK — workflowId={}", workflowId);

        workflowClient.newWorkflowStub(SubscriptionSagaWorkflow.class, workflowId)
            .paymentOk();
    }
}
