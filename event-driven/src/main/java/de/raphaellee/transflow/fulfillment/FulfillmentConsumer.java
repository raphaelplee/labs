package de.raphaellee.transflow.fulfillment;

import de.raphaellee.transflow.payment.PaymentProcessedEvent;
import de.raphaellee.transflow.payment.PaymentScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Component
class FulfillmentConsumer {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentConsumer.class);
    private static final int SLOW_FULFILLMENT_MARGIN_SECONDS = 5;

    private final FulfillmentService fulfillmentService;
    private final Executor slowFulfillment;

    FulfillmentConsumer(FulfillmentService fulfillmentService,
                        @Value("${saga.fulfillment-timeout-seconds}") int fulfillmentTimeoutSeconds) {
        this.fulfillmentService = fulfillmentService;
        this.slowFulfillment = CompletableFuture.delayedExecutor(
            fulfillmentTimeoutSeconds + SLOW_FULFILLMENT_MARGIN_SECONDS, TimeUnit.SECONDS);
    }

    @KafkaListener(topics = "payment.processed", groupId = "transflow-fulfillment")
    void consume(PaymentProcessedEvent event) {
        if (PaymentScenario.FULFILLMENT_TIMEOUT == event.scenario()) {
            log.info("Delaying fulfillment past the saga deadline — orderId={}", event.orderId());
            CompletableFuture.runAsync(() -> complete(event), slowFulfillment)
                .exceptionally(e -> {
                    log.error("Delayed fulfillment failed — orderId={}", event.orderId(), e);
                    return null;
                });
            return;
        }
        complete(event);
    }

    private void complete(PaymentProcessedEvent event) {
        fulfillmentService.complete(event.orderId(), event.subscriptionId());
        log.info("Fulfillment completed — orderId={}", event.orderId());
    }
}
