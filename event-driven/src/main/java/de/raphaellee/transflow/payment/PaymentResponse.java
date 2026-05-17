package de.raphaellee.transflow.payment;

import java.util.UUID;

public record PaymentResponse(UUID paymentId, UUID orderId, String status) {}
