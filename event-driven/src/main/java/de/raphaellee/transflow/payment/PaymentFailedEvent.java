package de.raphaellee.transflow.payment;

import org.springframework.modulith.events.Externalized;

@Externalized("payment.failed")
public record PaymentFailedEvent(String orderId, String subscriptionId) {}
