package de.raphaellee.transflow.fulfillment;

import org.springframework.modulith.events.Externalized;

@Externalized("fulfillment.completed")
public record FulfillmentCompletedEvent(String fulfillmentId, String orderId, String subscriptionId) {}
