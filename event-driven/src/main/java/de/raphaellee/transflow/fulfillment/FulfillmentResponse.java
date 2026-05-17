package de.raphaellee.transflow.fulfillment;

import java.time.Instant;
import java.util.UUID;

public record FulfillmentResponse(
    UUID fulfillmentId,
    UUID orderId,
    UUID subscriptionId,
    String status,
    Instant fulfilledAt
) {}
