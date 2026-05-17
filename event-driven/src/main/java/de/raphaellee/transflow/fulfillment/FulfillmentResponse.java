package de.raphaellee.transflow.fulfillment;

import java.time.Instant;
import java.util.UUID;

public record FulfillmentResponse(
    UUID fulfillmentId,
    UUID orderId,
    String subscriptionId,
    String status,
    Instant fulfilledAt
) {}
