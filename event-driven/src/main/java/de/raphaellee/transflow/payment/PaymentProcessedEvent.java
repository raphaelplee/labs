package de.raphaellee.transflow.payment;

import org.springframework.modulith.events.Externalized;
import java.util.UUID;

@Externalized("payment.processed")
public record PaymentProcessedEvent(String orderId, UUID subscriptionId, PaymentScenario scenario) {}
