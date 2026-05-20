# Weekend 2 Design — Event-Driven Saga with Temporal + Kafka

**Date:** 2026-05-16  
**Revised:** 2026-05-17 (post eng review)  
**Status:** Approved  
**Module:** `event-driven`  
**Live demo:** `transflow.raphaellee.de`

---

## Goal

Implement a subscription lifecycle saga (order → payment → fulfillment) as a fully working, publicly accessible demo. The saga starts from a domain event, not a direct orchestrator API call. Four Spring Modulith modules in one JVM communicate via Kafka events and Temporal signals. Full business failure scenarios are triggerable from a live HTML status page.

Success means: a hiring manager can open `transflow.raphaellee.de`, click a button, and watch a saga flow through its steps in real time — including compensation on failure.

---

## Architecture

One JVM, one Temporal Server, one Kafka broker.

```
transflow-core   Spring Boot + Spring Modulith   :8080   transflow.raphaellee.de
```

Four enforced Modulith modules inside `transflow-core`:

```
orchestration   Temporal workflow + Kafka consumers + saga REST API + HTML status page
order           REST API + publishes OrderCreatedEvent
payment         REST API + publishes PaymentProcessedEvent / PaymentFailedEvent
fulfillment     Kafka consumer + signals FULFILLMENT_DONE + fulfillment REST API
```

Spring Modulith enforces that no module may import another module's internals directly. ArchUnit verifies at build time. Temporal's task queue is the cross-module coordination mechanism — not direct Java calls.

### Flow

```
[Browser] → POST /api/orders (order module)
               → publishes OrderCreatedEvent (Modulith → Kafka order.created)
                   → orchestration Kafka consumer starts SubscriptionSagaWorkflow
                       → workflow awaits PAYMENT_OK or PAYMENT_FAILED signal

[Browser] → POST /api/payments/{orderId}/confirm (payment module)
               → publishes PaymentProcessedEvent (Modulith → Kafka payment.processed)
                   → orchestration consumer signals PAYMENT_OK to workflow
                   → fulfillment consumer reads payment.processed, does work,
                     signals FULFILLMENT_DONE to workflow
                       → workflow completes

[Browser] → POST /api/payments/{orderId}/fail (payment module)
               → publishes PaymentFailedEvent (Kafka payment.failed)
                   → orchestration consumer signals PAYMENT_FAILED → workflow compensates
```

### workflowId Convention

`workflowId = "saga-" + subscriptionId`

This is the canonical derivation rule across the entire system. The `orchestration` module uses it when starting a workflow. The `fulfillment` module uses it when sending the FULFILLMENT_DONE signal. No workflowId is passed over Kafka — it is always derived from `subscriptionId`.

### Signal Ordering

Temporal buffers signals that arrive before the workflow reaches the matching `Workflow.await`. If FULFILLMENT_DONE arrives while the workflow is still in AWAITING_PAYMENT, Temporal queues it. When the workflow reaches the fulfillment await after PAYMENT_OK, the queued signal is processed immediately. No ordering coordination needed.

### Why This Shape

- **Kafka is the genuine event boundary** — domain events cross module boundaries via Kafka topics, not direct method calls. Kafka UI shows the full saga trace.
- **Spring Modulith enforces domain isolation** — `payment` cannot import `order`; `fulfillment` cannot import `orchestration`. ArchUnit verifies at build time.
- **Temporal is the saga state machine** — the `orchestration` module owns no business logic, only workflow coordination. Status queries use the Temporal Visibility API.
- **One JVM** — right-sized for the CPX32 server. Module boundaries are enforced by tooling, not network topology.

---

## Docker Compose Stack

Eleven services, one internal Docker network. Nothing exposed to the host except Caddy (80/443).

| Service | Image | Internal port | External |
|---|---|---|---|
| `caddy` | `caddy:2` | 80/443 | — (is the proxy) |
| `postgres` | `postgres:17.10` | 5432 | SSH tunnel |
| `adminer` | `adminer:4` | 8080 | SSH tunnel (`localhost:5050`) |
| `elasticsearch` | `elasticsearch:8` | 9200 | — |
| `kafka` | `apache/kafka:4.2.0` | 9092 | — |
| `kafka-ui` | `kafbat/kafka-ui:v1.3.0` | 8090 | `kafka.raphaellee.de` |
| `temporal` | `temporalio/auto-setup:1.29.6.1` | 7233 | — |
| `temporal-ui` | `temporalio/ui:2.29.2` | 8233 | `temporal.raphaellee.de` |
| `transflow-core` | built locally | 8080 | `transflow.raphaellee.de` |

**Elasticsearch** enables Temporal advanced visibility (`listWorkflowExecutions` with filtering). Temporal auto-setup detects Elasticsearch at startup and switches to advanced visibility automatically. Required for `GET /api/sagas` to use `WorkflowType` and `StartTime` filters.

**Kafka topics** (created by init container at startup):
`order.created` · `payment.processed` · `payment.failed` · `fulfillment.completed`

**Kafka consumer group IDs:**
- `transflow-orchestration` — consumes `order.created`, `payment.processed`, `payment.failed`
- `transflow-fulfillment` — consumes `payment.processed`

Both groups receive every `payment.processed` message independently.

**Postgres schemas:**
- `temporal` — Temporal persistence (auto-created by `temporalio/auto-setup`)
- `transflow` — Spring Modulith event publication log + fulfillment records

**Kafka:** KRaft mode (no ZooKeeper). `KAFKA_CFG_PROCESS_ROLES=controller,broker`.

**Memory limits** (required — prevents OOM on CPX32 8 GB):

```yaml
elasticsearch:  mem_limit: 1g      # ES_JAVA_OPTS: -Xms512m -Xmx1g
temporal:       mem_limit: 512m
kafka:          mem_limit: 512m
transflow-core: mem_limit: 512m    # JAVA_OPTS: -Xmx400m
kafka-ui:       mem_limit: 256m
temporal-ui:    mem_limit: 128m
postgres:       mem_limit: 256m
caddy:          mem_limit: 64m
adminer:        mem_limit: 64m
# Total capped: ~3.3 GB. Leaves ~4.7 GB for OS + headroom.
```

**Startup dependency chain** (cold start reliability — required):

```yaml
elasticsearch:
  healthcheck:
    test: ["CMD-SHELL", "curl -sf http://localhost:9200/_cluster/health?wait_for_status=yellow&timeout=5s || exit 1"]
    interval: 15s
    timeout: 10s
    retries: 10
    start_period: 60s   # ES needs up to 60s before it accepts health checks

temporal:
  depends_on:
    postgres:
      condition: service_healthy   # pg_isready (existing)
    elasticsearch:
      condition: service_healthy   # wait for ES yellow status

transflow-core:
  depends_on:
    temporal:
      condition: service_healthy   # wait for Temporal gRPC port
  # Temporal healthcheck: temporalio/auto-setup exposes :7233 when ready
  # Use: nc -z localhost 7233 || exit 1  (or curl :7233, returns HTTP 200)
```

Rationale: Elasticsearch 8 takes 45-90 s on a cold start. Without this chain, `transflow-core` can boot before Temporal's advanced visibility is ready, causing the Temporal Java SDK to fail workflow listing silently. The chain guarantees a single `docker compose up` works without manual restarts.

**Adminer security:** not exposed via Caddy. Access via SSH tunnel:
```bash
ssh -L 5050:adminer:8080 user@raphaellee.de
# then open http://localhost:5050
```

---

## `transflow-core` — Spring Boot + Spring Modulith

**Maven module:** `transflow-core`  
**Dependencies:** Spring Boot 4.0.6, Spring Modulith 2.x, Temporal Java SDK, spring-kafka, springdoc-openapi-starter-webmvc-ui:3.0.3, Flyway, Postgres driver

### Module Structure

```
de.raphaellee.transflow/
├── orchestration/
│   ├── SubscriptionSagaWorkflow.java        # @WorkflowInterface
│   ├── SubscriptionSagaWorkflowImpl.java    # workflow logic
│   ├── SagaController.java                  # GET /api/sagas, GET /api/sagas/{id}
│   ├── OrderCreatedConsumer.java            # @KafkaListener(order.created) → start workflow
│   ├── PaymentProcessedConsumer.java        # @KafkaListener(payment.processed) → PAYMENT_OK signal
│   ├── PaymentFailedConsumer.java           # @KafkaListener(payment.failed) → PAYMENT_FAILED signal
│   └── SagaStatusMapper.java               # Temporal execution → SagaStatus DTO
├── order/
│   ├── OrderController.java                 # POST /api/orders, GET /api/orders/{id}
│   ├── OrderService.java
│   ├── Order.java                           # JPA aggregate
│   └── OrderCreatedEvent.java              # ApplicationEvent → externalized to order.created
├── payment/
│   ├── PaymentController.java               # POST /api/payments/{id}/confirm|fail
│   ├── PaymentService.java
│   ├── Payment.java
│   ├── PaymentProcessedEvent.java          # fields: orderId, subscriptionId, scenario
│   └── PaymentFailedEvent.java             # fields: orderId, subscriptionId
└── fulfillment/
    ├── FulfillmentConsumer.java             # @KafkaListener(payment.processed, group=transflow-fulfillment)
    ├── FulfillmentService.java              # does fulfillment work, saves record
    ├── FulfillmentRecord.java              # JPA entity
    └── FulfillmentController.java          # GET /api/fulfillments, GET /api/fulfillments/{orderId}
```

Spring Modulith externalizes `ApplicationEvent`s to Kafka via the event publication registry (outbox pattern — backed by Postgres `transflow` schema for delivery guarantee).

**ArchUnit rules:**
- `payment` must not import any class from `order`
- `fulfillment` must not import any class from `orchestration` or `order` or `payment` directly

### Temporal Workflow

```
SubscriptionSagaWorkflow:

START (triggered by order.created Kafka event)
  ↓
  Workflow.await(() -> paymentOk || paymentFailed)
  ↓ [PAYMENT_FAILED signal]
    → record PAYMENT_FAILED → END
  ↓ [PAYMENT_OK signal]
    → Workflow.await(Duration.ofSeconds(30), () -> fulfillmentDone)
    ↓ [timer fires — 30s elapsed, no FULFILLMENT_DONE]
      → record TIMED_OUT → END
    ↓ [FULFILLMENT_DONE signal received]
      → record COMPLETED → END
```

Timeout is configurable via environment variable (`SAGA_FULFILLMENT_TIMEOUT_SECONDS`, default 30).

### FulfillmentConsumer Behavior

```java
// FulfillmentConsumer consumes payment.processed (group: transflow-fulfillment)
// Derives workflowId from subscriptionId — never from the event payload
String workflowId = "saga-" + event.subscriptionId();

// Scenario routing
if ("fulfillment-timeout".equals(event.scenario())) {
    Thread.sleep(35_000); // sleep past the 30s workflow timer — timeout fires first
}

// Save fulfillment record + publish fulfillment.completed
fulfillmentService.complete(event.orderId(), event.subscriptionId());

// Signal the workflow — catch if already closed (timeout race)
try {
    workflowClient.newUntypedWorkflowStub(workflowId).signal("FULFILLMENT_DONE");
} catch (WorkflowNotFoundException e) {
    log.warn("Workflow {} already closed — FULFILLMENT_DONE signal discarded", workflowId);
}
```

### REST API

```
# Order module
POST /api/orders
  body: { "subscriptionId": "string" }
  → 201 { "orderId": "uuid", "subscriptionId": "string", "status": "CREATED" }
  → 409 if subscriptionId already has an active order

GET  /api/orders/{orderId}
  → 200 { "orderId", "subscriptionId", "status", "createdAt" }
  → 404 if not found

# Payment module
POST /api/payments/{orderId}/confirm?scenario=happy-path|fulfillment-timeout
  → 202 { "paymentId": "uuid", "orderId": "uuid", "status": "PROCESSED" }
  → 404 if orderId not found

POST /api/payments/{orderId}/fail
  → 202 { "paymentId": "uuid", "orderId": "uuid", "status": "FAILED" }
  → 404 if orderId not found

# Orchestration module
GET /api/sagas
  → uses Temporal listWorkflowExecutions (Visibility API, single gRPC call)
  → [ { "sagaId", "subscriptionId", "status", "scenario", "startedAt", "updatedAt", "steps": [...] } ]

GET /api/sagas/{sagaId}
  → uses Temporal describeWorkflowExecution (history for step detail)
  → { "sagaId", "subscriptionId", "status", "scenario", "startedAt", "updatedAt",
      "steps": [
        { "name": "ORDER_CREATED",           "status": "COMPLETED", "completedAt": "..." },
        { "name": "AWAITING_PAYMENT",        "status": "COMPLETED", "completedAt": "..." },
        { "name": "FULFILLMENT_PROCESSING",  "status": "RUNNING",   "completedAt": null }
      ],
      "error": null }

# Fulfillment module
GET /api/fulfillments
  → [ { "fulfillmentId", "orderId", "subscriptionId", "status", "fulfilledAt" } ]

GET /api/fulfillments/{orderId}
  → { "fulfillmentId", "orderId", "subscriptionId", "status", "fulfilledAt" }
  → 404 if not found
```

**Saga status values:** `AWAITING_PAYMENT` · `PAYMENT_FAILED` · `FULFILLMENT_PROCESSING` · `COMPLETED` · `TIMED_OUT`

**OpenAPI / Swagger UI:** `transflow.raphaellee.de/swagger-ui/index.html`  
**CORS:** not needed — HTML status page is served by the same origin (`transflow-core`)

---

## HTML Status Page

Served by `transflow-core` (orchestration module) at `transflow.raphaellee.de`. Single `index.html`, vanilla JS, no framework. Polls `GET /api/sagas` every 2 seconds.

**Architecture blurb** (shown above the trigger panel — always visible):

> A subscription lifecycle saga — order → payment → fulfillment — orchestrated by **Temporal** and triggered via **Kafka** domain events. Built with **Spring Boot 4** + **Spring Modulith** (four enforced module boundaries in one JVM). Each button makes a single REST call — step through the saga manually to watch each state transition in real time.

**Layout:**

Nav bar: `transflow` logo · connection status dot (🟢 connected / 🔴 backend unreachable) · links: Temporal UI (demo / your-demo-password) · Kafka UI (demo / your-demo-password) · Swagger UI · GitHub

Left panel — **Step-by-step trigger** (one button = one REST call):

- **Step 1 — Create Order** (always enabled)
  - `POST /api/orders` with a generated `subscriptionId`
  - On success: step badge turns green ✓, active orderId shown in context box, Step 2 buttons unlock
- **Step 2 — Choose a payment outcome** (enabled only after Step 1)
  - 🟢 `POST /api/payments/{orderId}/confirm?scenario=HAPPY_PATH`
  - 🔴 `POST /api/payments/{orderId}/fail`
  - 🟠 `POST /api/payments/{orderId}/confirm?scenario=FULFILLMENT_TIMEOUT`
  - Active orderId displayed in a blue-tinted context box between steps — no guessing which order is being acted on
  - After any Step 2 action: all payment buttons disable, step badges reset for the next run
- **Step 3 — Fulfillment** (no button — automatic)
  - Explanatory text only: fulfillment completes via Kafka after payment confirmed
- **Idempotency Demo** (standalone group, always enabled)
  - Fires `POST /api/orders` twice in parallel with the same `subscriptionId`
  - Hint updates to show "Responses: 201 + 409"

**Button feedback:** Each button disables and shows a ⏳ spinner on click. Re-enables after the response. Payment buttons stay disabled after use — user must create a new order (Step 1) to run another scenario. Step number badges cycle: pending (grey) → active (blue) → done (green ✓).

**`activeOrder` state:** JS variable `{ orderId, subscriptionId }` is set after Step 1 succeeds and cleared after Step 2 completes. The current saga card gets a blue border (`.saga-card.current`) to visually link the live card to the step flow.

Right panel — **Live Saga List**: each saga as a card with step timeline dots (grey/orange/green/red), status badge, and timestamps.

**Backend status indicator:** The nav bar shows a small connection dot. When `GET /api/sagas` returns successfully → green. When it fails (network error, 5xx, timeout) → red with "Backend unreachable — retrying". This distinguishes "no sagas yet" from "backend is down" — critical for cold-start scenarios where Elasticsearch/Temporal may still be initializing.

---

## Failure Scenarios

| Scenario | How to trigger | Expected outcome |
|---|---|---|
| Payment failure | `POST /api/payments/{id}/fail` | Saga → `PAYMENT_FAILED`; `payment.failed` visible in Kafka UI |
| Fulfillment timeout | `POST /api/payments/{id}/confirm?scenario=fulfillment-timeout` | FulfillmentConsumer sleeps 35s; workflow timer fires at 30s; saga → `TIMED_OUT`; stale FULFILLMENT_DONE signal caught and discarded |
| Idempotency (running) | Same `subscriptionId` while first saga is running | Second `order.created` → `WorkflowExecutionAlreadyStarted` — existing workflow returned, no duplicate |

**Note on idempotency:** `WorkflowExecutionAlreadyStarted` only fires while the first workflow is still RUNNING. If it has already completed or failed, a second order with the same `subscriptionId` starts a fresh saga. For the demo, the "Test Idempotency" button fires two requests in rapid succession — the first workflow will be running when the second arrives.

---

## Testing Strategy

### Fast path (runs on every push/PR — no Testcontainers)

**`SubscriptionSagaWorkflowTest`** — `TestWorkflowEnvironment` (Temporal in-process, no Docker, ~10 ms per test):
1. PAYMENT_OK signal → workflow advances to FULFILLMENT_PROCESSING state
2. PAYMENT_FAILED signal → workflow reaches PAYMENT_FAILED end state
3. PAYMENT_OK + FULFILLMENT_DONE signal → workflow COMPLETED
4. PAYMENT_OK + timer fires before FULFILLMENT_DONE → workflow TIMED_OUT

**ArchUnit tests** — no containers, verifies cross-module import rules.

**`@ApplicationModuleTest`** — Spring Modulith's per-module isolation tests, no Kafka, no Temporal.

These three are tagged `@Tag("unit")` and included in the default Surefire run.

### Integration path (runs in separate `integration-test` CI job on PR to main)

**`transflow-core` integration tests** — Testcontainers: Kafka (KRaft) + Postgres, tagged `@Tag("integration")`:
- `POST /api/orders` → verify `order.created` published to Kafka
- `POST /api/payments/{id}/confirm` → verify `payment.processed` published with correct fields (`orderId`, `subscriptionId`, `scenario`)
- `POST /api/payments/{id}/fail` → verify `payment.failed` published
- `POST /api/orders` duplicate `subscriptionId` → 409
- `POST /api/payments/{unknownId}/confirm` → 404
- Full saga: `order.created` → `payment.processed` → `FULFILLMENT_DONE` signal → `COMPLETED`
- Compensation: `payment.failed` → `PAYMENT_FAILED`
- Idempotency: two rapid `order.created` same `subscriptionId` → single workflow

**Test isolation:** `@Tag("unit")` tests and `@Tag("integration")` tests run in separate Maven Surefire/Failsafe executions. `TestWorkflowEnvironment` and Testcontainers Temporal never load in the same JVM — no port conflicts, no worker registration collisions.

### CI workflows

**Existing** `ci.yml`: `mvn test -P skip-integration-tests` — fast path only, runs on push and PR.

**New** `integration-tests.yml`: `mvn verify -P integration-tests` — runs `@Tag("integration")` suite with Testcontainers, triggers on PR to `main`. Separate job, not blocking the fast path.

---

## Success Criteria

- `transflow.raphaellee.de` loads and shows live saga status
- Happy path completes end-to-end: order → payment → fulfillment, all steps green
- Payment failure shows PAYMENT_FAILED with `payment.failed` message visible in `kafka.raphaellee.de`
- Fulfillment timeout shows TIMED_OUT after 30 seconds; stale signal discarded cleanly
- Idempotency: two rapid orders with same subscriptionId → single workflow in `temporal.raphaellee.de`
- `kafka.raphaellee.de` shows all four topics with messages
- `temporal.raphaellee.de` shows workflow executions, history, and state transitions
- `transflow.raphaellee.de/swagger-ui/index.html` — all endpoints documented
- All unit tests (Workflow + ArchUnit + @ApplicationModuleTest) green in fast CI
- Integration test suite green in `integration-tests` CI job
- ArchUnit cross-module boundary rules enforced

---

## NOT in Scope

- Multiple JVMs / separate microservices — deliberately consolidated into one Modulith for CPX32 RAM budget. Module boundaries enforced by tooling.
- Kafka DLQ (dead letter queue) — deferred to post-Weekend-5 per TODOS.md
- ADR directory — deferred to post-Weekend-5
- HTMX frontend — stretch goal, Weekend 8
- Multi-region Temporal — not in scope for this project
- Elasticsearch clustering — single node for dev; `discovery.type=single-node`
- Grafana / observability — Weekend 5

## What Already Exists

- `compose/docker-compose.yml` — Caddy + Postgres running; will be extended (not replaced)
- `compose/Caddyfile` — existing TLS routes; new `transflow.raphaellee.de` route added alongside existing ones
- `event-driven/pom.xml` — stub module; restructured into `transflow-core` child module
- `.github/workflows/ci.yml` — fast test job; extended with new `integration-tests.yml` alongside it

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
|--------|---------|-----|------|--------|----------|
| CEO Review | `/plan-ceo-review` | Scope & strategy | 0 | — | — |
| Codex Review | `/codex review` | Independent 2nd opinion | 0 | — | — |
| Eng Review | `/plan-eng-review` | Architecture & tests (required) | 1 | CLEAR | 7 issues, 0 critical gaps |
| Design Review | `/plan-design-review` | UI/UX gaps | 0 | — | — |
| DX Review | `/plan-devex-review` | Developer experience gaps | 1 | CLEAR | 4 issues, 0 critical gaps |

**VERDICT: ENG + DX CLEARED — ready to implement.**
