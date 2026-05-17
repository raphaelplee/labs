package de.raphaellee.transflow.payment;

import org.springframework.modulith.events.Externalized;
import java.util.UUID;

@Externalized("payment.failed")
public record PaymentFailedEvent(String orderId, UUID subscriptionId) {}
