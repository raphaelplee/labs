# Weekend 2 Design — Event-Driven Saga with Temporal + Kafka

**Date:** 2026-05-16  
**Status:** Approved  
**Module:** `event-driven`  
**Live demos:** `transflow.raphaellee.de` · `saga.raphaellee.de` · `fulfillment.raphaellee.de`

---

## Goal

Implement a subscription lifecycle saga (order → payment → fulfillment) as a fully working, publicly accessible demo. The saga starts from a domain event, not a direct orchestrator API call. Three independent Spring Boot processes communicate via Kafka and Temporal. Full business failure scenarios are triggerable from a live HTML status page.

Success means: a hiring manager can open `saga.raphaellee.de`, click a button, and watch a saga flow through its steps in real time — including compensation on failure.

---

## Architecture

Three JVMs, one Temporal Server, one Kafka broker.

```
transflow-core          Spring Boot + Spring Modulith    :8080   transflow.raphaellee.de
transflow-orchestrator  Spring Boot (plain)              :8082   saga.raphaellee.de
transflow-fulfillment   Spring Boot (plain)              :8081   fulfillment.raphaellee.de
```

### Flow

```
[Browser] → POST /api/orders (core)
               → order module publishes order.created (Modulith → Kafka)
                   → orchestrator Kafka consumer starts SubscriptionSagaWorkflow
                       → workflow waits for payment signal

[Browser] → POST /api/payments/{orderId}/confirm (core)
               → payment module publishes payment.processed (Modulith → Kafka)
                   → orchestrator Kafka consumer signals PAYMENT_OK to workflow
                       → workflow waits for FULFILLMENT_DONE signal

[Kafka] payment.processed consumed by transflow-fulfillment
               → FulfillmentActivity Temporal worker executes
                   → publishes fulfillment.completed
                   → Temporal signals FULFILLMENT_DONE to orchestrator workflow
                       → workflow completes
```

### Why This Shape

- **Kafka is the genuine integration boundary** — `payment.processed` crosses two process boundaries (core → orchestrator, core → fulfillment) without HTTP coupling.
- **Spring Modulith enforces domain boundaries** — `payment` module cannot import `order` directly; ArchUnit verifies this at build time.
- **Temporal is the saga state machine** — the orchestrator owns no business logic, only workflow coordination. Status queries read Temporal workflow history.
- **Fulfillment is the extracted microservice** — demonstrates the modular monolith → microservice extraction pattern.

---

## Docker Compose Stack

Nine services, one internal Docker network. Nothing exposed to the host except Caddy (80/443).

| Service | Image | Internal port | External |
|---|---|---|---|
| `caddy` | `caddy:2` | 80/443 | — (is the proxy) |
| `postgres` | `postgres:17.10` | 5432 | SSH tunnel |
| `adminer` | `adminer:4` | 8080 | SSH tunnel (`localhost:5050`) |
| `kafka` | `apache/kafka:4.2.0` | 9092 | — |
| `kafka-ui` | `kafbat/kafka-ui:v1.3.0` | 8090 | `kafka.raphaellee.de` |
| `temporal` | `temporalio/auto-setup:1.29.6.1` | 7233 | — |
| `temporal-ui` | `temporalio/ui:2.29.2` | 8233 | `temporal.raphaellee.de` |
| `transflow-core` | built locally | 8080 | `transflow.raphaellee.de` |
| `transflow-orchestrator` | built locally | 8082 | `saga.raphaellee.de` |
| `transflow-fulfillment` | built locally | 8081 | `fulfillment.raphaellee.de` |

**Kafka topics** (created by init container at startup):
`order.created` · `payment.processed` · `payment.failed` · `fulfillment.completed`

**Postgres schemas:**
- `temporal` — Temporal persistence (auto-created by `temporalio/auto-setup`)
- `transflow` — Spring Modulith event publication log + fulfillment records

**Kafka:** KRaft mode (no ZooKeeper). `KAFKA_CFG_PROCESS_ROLES=controller,broker`.

**Adminer security:** not exposed via Caddy. Access via SSH tunnel:
```bash
ssh -L 5050:adminer:8080 user@raphaellee.de
# then open http://localhost:5050
```

---

## `transflow-core` — Spring Boot + Spring Modulith

**Maven module:** `transflow-core`  
**Dependencies:** Spring Boot 4.0.6, Spring Modulith 2.x, spring-kafka, springdoc-openapi-starter-webmvc-ui:3.0.3, Flyway, Postgres driver

### Module Structure

```
de.raphaellee.transflow/
├── order/
│   ├── OrderController.java       # POST /api/orders, GET /api/orders/{id}
│   ├── OrderService.java
│   ├── Order.java                 # aggregate
│   └── OrderCreatedEvent.java     # ApplicationEvent → externalized to order.created
└── payment/
    ├── PaymentController.java     # POST /api/payments/{orderId}/confirm|fail
    ├── PaymentService.java
    ├── Payment.java
    ├── PaymentProcessedEvent.java # → payment.processed (carries scenario field)
    └── PaymentFailedEvent.java    # → payment.failed
```

Spring Modulith externalizes all `ApplicationEvent`s to Kafka automatically via the event publication registry (backed by Postgres `transflow` schema). The `scenario` field is included in the `PaymentProcessedEvent` payload so `transflow-fulfillment` can read it without extra coordination.

ArchUnit rule in test suite: `payment` package must not import any class from `order` package.

### REST API

```
POST /api/orders
  body: { "subscriptionId": "string" }
  → 201 { "orderId": "uuid", "subscriptionId": "string", "status": "CREATED" }

GET  /api/orders/{orderId}
  → 200 { "orderId", "subscriptionId", "status", "createdAt" }

POST /api/payments/{orderId}/confirm?scenario=happy-path|fulfillment-timeout
  → 202 { "paymentId": "uuid", "orderId": "uuid", "status": "PROCESSED" }

POST /api/payments/{orderId}/fail
  → 202 { "paymentId": "uuid", "orderId": "uuid", "status": "FAILED" }
```

**OpenAPI:** `transflow.raphaellee.de/swagger-ui/index.html`  
**CORS:** enabled for `saga.raphaellee.de` (status page calls core endpoints directly from browser)

---

## `transflow-orchestrator` — Plain Spring Boot

**Maven module:** `transflow-orchestrator`  
**Dependencies:** Spring Boot 4.0.6, Temporal Java SDK, spring-kafka, springdoc-openapi-starter-webmvc-ui:3.0.3

### Responsibilities

**Kafka consumers:**
- `order.created` → extract `orderId` + `subscriptionId` → start `SubscriptionSagaWorkflow` with `workflowId = "saga-" + subscriptionId` (idempotency key)
- `payment.processed` → signal workflow `PAYMENT_OK`
- `payment.failed` → signal workflow `PAYMENT_FAILED`

**Temporal workflow — `SubscriptionSagaWorkflow`:**

```
START
  ↓ wait for PAYMENT_OK or PAYMENT_FAILED signal (no timeout — payment is manual in demo)
  ↓ [PAYMENT_FAILED] → record PAYMENT_FAILED status → END (compensated)
  ↓ [PAYMENT_OK] → dispatch FulfillmentActivity task (to transflow-fulfillment worker)
      scheduleToCloseTimeout: 30s (dev), configurable via env
  ↓ [timeout] → record TIMED_OUT status → END (compensated)
  ↓ [FULFILLMENT_DONE signal] → record COMPLETED status → END
```

No business logic in the workflow — only state transitions and signal handling.

### REST API

```
GET /api/sagas
  → [ { "sagaId", "subscriptionId", "status", "scenario", "startedAt", "updatedAt", "steps": [...] } ]

GET /api/sagas/{sagaId}
  → { "sagaId", "subscriptionId", "status", "scenario", "startedAt", "updatedAt",
      "steps": [
        { "name": "ORDER_CREATED",        "status": "COMPLETED", "completedAt": "..." },
        { "name": "PAYMENT_PROCESSING",   "status": "COMPLETED", "completedAt": "..." },
        { "name": "FULFILLMENT_PROCESSING","status": "RUNNING",  "completedAt": null }
      ],
      "error": null }
```

**Saga status values:** `AWAITING_PAYMENT` · `PAYMENT_FAILED` · `FULFILLMENT_PROCESSING` · `COMPLETED` · `TIMED_OUT`

Status is derived by querying Temporal workflow history — no separate status store needed.

**OpenAPI:** `saga.raphaellee.de/swagger-ui/index.html`

---

## `transflow-fulfillment` — Plain Spring Boot

**Maven module:** `transflow-fulfillment`  
**Dependencies:** Spring Boot 4.0.6, Temporal Java SDK, spring-kafka, springdoc-openapi-starter-webmvc-ui:3.0.3, Flyway, Postgres driver

### Package Structure

```
de.raphaellee.transflow.fulfillment/
├── FulfillmentConsumer.java      # @KafkaListener on payment.processed
├── FulfillmentActivity.java      # @ActivityInterface
├── FulfillmentActivityImpl.java  # reads scenario, sleeps if timeout scenario
├── FulfillmentRecord.java        # JPA entity saved on completion
├── FulfillmentController.java    # GET /api/fulfillments, GET /api/fulfillments/{orderId}
└── FulfillmentApplication.java
```

### Behavior

`FulfillmentConsumer` consumes `payment.processed`, reads the `scenario` field:
- `happy-path` (default): activity completes normally, saves `FulfillmentRecord` to Postgres, publishes `fulfillment.completed` to Kafka
- `fulfillment-timeout`: activity sleeps past `scheduleToCloseTimeout` — Temporal cancels the task, workflow compensates

The `FulfillmentActivity` Temporal worker registers against the same Temporal Server as `transflow-orchestrator`. When it completes, it signals `FULFILLMENT_DONE` back to the waiting workflow via `workflowStub.signal()`.

### REST API

```
GET /api/fulfillments
  → [ { "fulfillmentId", "orderId", "subscriptionId", "status", "fulfilledAt" } ]

GET /api/fulfillments/{orderId}
  → { "fulfillmentId", "orderId", "subscriptionId", "status", "fulfilledAt" }
```

**OpenAPI:** `fulfillment.raphaellee.de/swagger-ui/index.html`

---

## HTML Status Page

Served by `transflow-orchestrator` at `saga.raphaellee.de`. Single `index.html`, vanilla JS, no framework. Polls `GET /api/sagas` every 2 seconds.

**Layout:**

Left panel — **Trigger**:
- "Happy Path" → POST /api/orders → POST /api/payments/{id}/confirm
- "Payment Failure" → POST /api/orders → POST /api/payments/{id}/fail
- "Fulfillment Timeout" → POST /api/orders → POST /api/payments/{id}/confirm?scenario=fulfillment-timeout
- "Test Idempotency" → same POST /api/orders (fixed subscriptionId) twice

Right panel — **Live Saga List**: each saga as a card with step timeline dots (grey/yellow/green/red) and status badge.

Nav bar links: Temporal UI · Kafka UI · Core Swagger · Orchestrator Swagger · Fulfillment Swagger

---

## Failure Scenarios

| Scenario | How to trigger | Expected outcome |
|---|---|---|
| Payment failure | `POST /api/payments/{id}/fail` | Saga status → `PAYMENT_FAILED`; `payment.failed` visible in Kafka UI |
| Fulfillment timeout | `POST /api/payments/{id}/confirm?scenario=fulfillment-timeout` | FulfillmentActivity cancelled after 30s; saga → `TIMED_OUT` |
| Idempotency | Same `subscriptionId` posted twice | Second `order.created` triggers `workflowAlreadyStarted` — existing workflow returned, no duplicate |

---

## Testing Strategy

Each Maven module has its own integration test suite, all using Testcontainers.

**`transflow-core`:**
- Testcontainers: Kafka (KRaft) + Postgres
- `POST /api/orders` → verify `order.created` published to Kafka
- `POST /api/payments/{id}/confirm` → verify `payment.processed` published (with scenario field)
- `POST /api/payments/{id}/fail` → verify `payment.failed` published
- `@ApplicationModuleTest` for each Modulith module in isolation
- ArchUnit: no cross-module imports

**`transflow-fulfillment`:**
- Testcontainers: Kafka + Temporal in-process test server (Temporal Java SDK)
- Happy path: consume `payment.processed` → verify `fulfillment.completed` published + DB record saved
- Timeout path: verify activity cancellation, no record saved

**`transflow-orchestrator`:**
- Testcontainers: Kafka + Temporal in-process test server
- Full saga: `order.created` → `payment.processed` → `FULFILLMENT_DONE` signal → saga `COMPLETED`
- Compensation: `payment.failed` → saga `PAYMENT_FAILED`
- Idempotency: two `order.created` with same `subscriptionId` → single workflow

**CI:** existing `skip-integration-tests` profile for fast push/PR builds. Integration tests run in a separate `integration-test` CI job on PR to `main`.

---

## Success Criteria

- `saga.raphaellee.de` loads and shows live saga status
- Happy path completes end-to-end: order → payment → fulfillment, all steps green
- Payment failure shows PAYMENT_FAILED status with compensation visible in Temporal UI
- Fulfillment timeout shows TIMED_OUT after 30 seconds
- Idempotency: second order with same subscriptionId does not create a second saga
- `kafka.raphaellee.de` shows all four topics with messages
- `temporal.raphaellee.de` shows workflow executions and history
- `transflow.raphaellee.de/swagger-ui/index.html` — all order + payment endpoints documented
- `saga.raphaellee.de/swagger-ui/index.html` — saga status endpoints documented
- `fulfillment.raphaellee.de/swagger-ui/index.html` — fulfillment list endpoint documented
- All three integration test suites green in CI
- ArchUnit cross-module rule enforced in `transflow-core` test build
