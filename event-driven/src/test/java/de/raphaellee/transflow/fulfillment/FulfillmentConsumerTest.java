package de.raphaellee.transflow.fulfillment;

import de.raphaellee.transflow.payment.PaymentProcessedEvent;
import de.raphaellee.transflow.payment.PaymentScenario;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@Tag("unit")
class FulfillmentConsumerTest {

    private final FulfillmentService fulfillmentService = mock(FulfillmentService.class);
    private final FulfillmentConsumer consumer = new FulfillmentConsumer(fulfillmentService, 0);

    @Test
    void happyPath_completesOnTheListenerThread() {
        var event = event(PaymentScenario.HAPPY_PATH);

        consumer.consume(event);

        verify(fulfillmentService).complete(event.orderId(), event.subscriptionId());
    }

    @Test
    void timeoutScenario_releasesTheListenerThreadAndCompletesAfterTheDeadline() {
        var event = event(PaymentScenario.FULFILLMENT_TIMEOUT);

        long started = System.nanoTime();
        consumer.consume(event);
        var elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(elapsed).isLessThan(Duration.ofSeconds(1));
        verify(fulfillmentService, never()).complete(any(), any());
        verify(fulfillmentService, timeout(10_000)).complete(event.orderId(), event.subscriptionId());
    }

    private static PaymentProcessedEvent event(PaymentScenario scenario) {
        return new PaymentProcessedEvent(UUID.randomUUID(), UUID.randomUUID(), scenario);
    }
}
