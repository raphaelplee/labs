package de.raphaellee.transflow.orchestration;

import java.time.Instant;
import java.util.List;

public record SagaStatus(
    String sagaId,
    String subscriptionId,
    String status,
    String scenario,
    Instant startedAt,
    Instant updatedAt,
    List<SagaStep> steps,
    String error
) {}
