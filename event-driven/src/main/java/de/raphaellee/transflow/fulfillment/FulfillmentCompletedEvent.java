package de.raphaellee.transflow.fulfillment;

import org.springframework.modulith.events.Externalized;
import java.util.UUID;

@Externalized("fulfillment.completed")
public record FulfillmentCompletedEvent(UUID fulfillmentId, UUID orderId, UUID subscriptionId) {}
