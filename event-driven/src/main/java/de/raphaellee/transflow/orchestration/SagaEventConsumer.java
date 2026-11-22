package de.raphaellee.transflow.orchestration;

import de.raphaellee.transflow.fulfillment.FulfillmentCompletedEvent;
import de.raphaellee.transflow.order.OrderCreatedEvent;
import de.raphaellee.transflow.payment.PaymentFailedEvent;
import de.raphaellee.transflow.payment.PaymentProcessedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class SagaEventConsumer {

    private final SagaClient sagaClient;

    SagaEventConsumer(SagaClient sagaClient) {
        this.sagaClient = sagaClient;
    }

    @KafkaListener(topics = "order.created", groupId = "transflow-orchestration")
    void orderCreated(OrderCreatedEvent event) {
        sagaClient.start(event.orderId(), event.subscriptionId());
    }

    @KafkaListener(topics = "payment.processed", groupId = "transflow-orchestration")
    void paymentProcessed(PaymentProcessedEvent event) {
        sagaClient.signal(event.orderId(), event.subscriptionId(), SubscriptionSagaWorkflow::paymentOk);
    }

    @KafkaListener(topics = "payment.failed", groupId = "transflow-orchestration")
    void paymentFailed(PaymentFailedEvent event) {
        sagaClient.signal(event.orderId(), event.subscriptionId(), SubscriptionSagaWorkflow::paymentFailed);
    }

    @KafkaListener(topics = "fulfillment.completed", groupId = "transflow-orchestration",
        properties = "auto.offset.reset=latest")
    void fulfillmentCompleted(FulfillmentCompletedEvent event) {
        sagaClient.signal(event.orderId(), event.subscriptionId(), SubscriptionSagaWorkflow::fulfillmentDone);
    }
}
