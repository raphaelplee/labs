package de.raphaellee.transflow.order;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(UUID orderId, String subscriptionId, String status, Instant createdAt) {}
