package de.raphaellee.transflow.fulfillment;

import org.springframework.modulith.events.Externalized;
import java.util.UUID;

@Externalized("fulfillment.completed")
public record FulfillmentCompletedEvent(String fulfillmentId, String orderId, UUID subscriptionId) {}
