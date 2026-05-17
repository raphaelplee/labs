package de.raphaellee.transflow.payment;

import org.springframework.modulith.events.Externalized;

@Externalized("payment.processed")
public record PaymentProcessedEvent(String orderId, String subscriptionId, String scenario) {}
