package de.raphaellee.transflow.orchestration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SagaStatus(
    String sagaId,
    UUID subscriptionId,
    String status,
    String scenario,
    Instant startedAt,
    Instant updatedAt,
    List<SagaStep> steps,
    String error
) {}
