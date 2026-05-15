# event-driven

Subscription lifecycle saga using Temporal for orchestration and Kafka for event streaming.

## Architectural Decision

**Temporal for saga orchestration over Kafka Streams.**

Temporal provides durable execution and explicit workflow state. A failed step retries with full context — no manual offset management, no dead-letter queue ceremony. Kafka Streams could coordinate the same flow, but at the cost of implementing retry/compensation logic by hand across stream topology.

## Trade-off

Temporal adds an infra component — one more process to run, one more thing to operate. Kafka Streams is zero-overhead: it runs inside the Spring Boot process. For a portfolio demo with a clear saga boundary, the operational clarity of Temporal's workflow UI outweighs the complexity cost.

## NOT in Scope

Multi-region Temporal deployment. This module runs a single Temporal server on the same Compose stack as the application.

## Reference

[Temporal documentation — workflows](https://docs.temporal.io/workflows)
