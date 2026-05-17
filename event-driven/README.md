# transflow-core

Subscription lifecycle saga — order → payment → fulfillment — using Temporal, Kafka, and Spring Modulith.

**Live demo:** https://transflow.raphaellee.de  
**Temporal UI:** https://temporal.raphaellee.de  
**Kafka UI:** https://kafka.raphaellee.de  
**Swagger:** https://transflow.raphaellee.de/swagger-ui/index.html

## Architecture

```
Spring Boot 4 (single JVM)
├── module: orchestration  — SubscriptionSagaWorkflow (Temporal) + Kafka consumers
├── module: order          — Order entity, REST API, order.created event
├── module: payment        — Payment entity, REST API, payment.processed/failed events
└── module: fulfillment    — FulfillmentRecord entity, Kafka consumer, fulfillment.completed event

Module boundaries enforced by Spring Modulith + ArchUnit (cross-package imports fail CI).
```

## Kafka Topic API Contract

These topics are the **public integration surface** of transflow-core. A future Rust IoT or Go service can consume/produce to these topics using the schemas below.

| Topic | Producer | Consumers | Schema |
|-------|----------|-----------|--------|
| `order.created` | order module | transflow-orchestration | `{"orderId": "UUID", "subscriptionId": "UUID"}` |
| `payment.processed` | payment module | transflow-orchestration, transflow-fulfillment | `{"orderId": "UUID", "subscriptionId": "UUID", "scenario": "string"}` |
| `payment.failed` | payment module | transflow-orchestration | `{"orderId": "UUID", "subscriptionId": "UUID"}` |
| `fulfillment.completed` | fulfillment module | — (audit only) | `{"fulfillmentId": "UUID", "orderId": "UUID", "subscriptionId": "UUID"}` |

**Key convention:** none (null key). Messages are not keyed; ordering within a topic is not required.

**WorkflowId convention:** `"saga-" + subscriptionId.toString()` (e.g. `saga-018f1234-dead-7000-beef-000000000001`)

## Saga State Machine

```
AWAITING_PAYMENT
  ├── [paymentOk signal]      → FULFILLMENT_PROCESSING
  │     ├── [fulfillmentDone] → COMPLETED
  │     └── [30s timeout]    → TIMED_OUT
  └── [paymentFailed signal]  → PAYMENT_FAILED
```

## Module Dependencies

```
orchestration → order, payment, fulfillment (all public APIs)
payment       → order (OrderService public API only)
fulfillment   → payment (PaymentProcessedEvent public record)
order         → (none)
```

## Running Locally

```bash
cd compose
cp .env.example .env  # set POSTGRES_PASSWORD
docker compose up -d
# Wait ~2 minutes for Temporal + Elasticsearch to be ready
# App available at http://localhost:8080
```
