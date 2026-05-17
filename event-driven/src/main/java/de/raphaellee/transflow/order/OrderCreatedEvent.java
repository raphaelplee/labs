package de.raphaellee.transflow.order;

import org.springframework.modulith.events.Externalized;

import java.util.UUID;

@Externalized("order.created")
public record OrderCreatedEvent(String orderId, UUID subscriptionId) {}
