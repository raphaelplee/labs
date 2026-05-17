package de.raphaellee.transflow.order;

import org.springframework.modulith.events.Externalized;

@Externalized("order.created")
public record OrderCreatedEvent(String orderId, String subscriptionId) {}
