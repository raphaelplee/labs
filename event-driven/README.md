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

Module boundaries are declared by `@ApplicationModule(allowedDependencies = …)` in each
`package-info.java` and checked by four ArchUnit rules in `ArchUnitTest`. Spring Modulith's own
`ApplicationModules.of(…).verify()` pass — which would enforce the same declarations — is not wired.
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

**Serialization:** Spring Modulith publishes via `ByteArraySerializer` — messages are raw JSON bytes with no `__TypeId__` headers. Consumers use `ByteArrayDeserializer` (Kafka client layer) + `ByteArrayJacksonJsonMessageConverter` registered as a single `@Bean` (Spring layer). Spring Boot 4 auto-wires a single `RecordMessageConverter` bean to the listener factory via `ObjectProvider.getIfUnique()`; the target type is inferred from the `@KafkaListener` method parameter. Note: `ByteArrayJsonMessageConverter` is deprecated since Spring Kafka 4.0 — use `ByteArrayJacksonJsonMessageConverter`.

**WorkflowId convention:** `"saga-" + subscriptionId.toString()` (e.g. `saga-<subscriptionId>`).
The prefix is written literally in five places — the four Kafka consumers and the status mapper —
and is the only link between a subscription and its workflow.

## Saga State Machine

```
AWAITING_PAYMENT
  ├── [paymentOk signal]      → FULFILLMENT_PROCESSING
  │     ├── [fulfillmentDone] → COMPLETED
  │     └── [timeout]         → TIMED_OUT
  └── [paymentFailed signal]  → PAYMENT_FAILED
```

The `/api/sagas` endpoint returns these internal state names verbatim in the `status` field.
`PAYMENT_FAILED`, `TIMED_OUT`, and `COMPLETED` all exit via a normal workflow `return`, so
Temporal's external execution status is `COMPLETED` for all three — it cannot distinguish
between them. The API works around this by calling the `getStatus()` `@QueryMethod` on every
workflow (Temporal supports querying closed workflows by replaying history). The Temporal-level
status is only a fallback if the query call fails.

## Module Dependencies

```
orchestration → order, payment, fulfillment (all public APIs)
payment       → order (OrderService public API only)
fulfillment   → payment (PaymentProcessedEvent public record)
order         → (none)
```

## Why Temporal + Kafka + Spring Modulith Together?

**Spring Modulith** enforces bounded contexts inside a single JVM. Four modules with
ArchUnit-verified boundaries — no accidental cross-module imports. Easier to reason
about than microservices; harder to accidentally couple than a flat package structure.

**Temporal** manages the saga state machine. Workflow history is durable — if the
JVM restarts mid-saga, execution resumes from the last checkpoint. Without Temporal,
you'd build that recovery logic yourself (and get it wrong under concurrent load).

**Kafka** is both the integration surface and the internal bus. Every cross-module event is
`@Externalized` to a topic and picked up by another module's `@KafkaListener`, so no module ever
invokes another's listeners in-process — `@ApplicationModuleListener` is not used anywhere. Kafka
also carries events that cross process boundaries: a future Rust IoT service or Go analytics
consumer can subscribe to `payment.processed` without touching this JVM.

The combination answers a common design question: when do you use the message broker
vs the workflow engine? Kafka = fan-out to unknown consumers. Temporal = orchestrate
a known sequence with compensation and timeout. They solve different problems.

## Running Locally

```bash
cd compose
cp .env.example .env  # fill in POSTGRES_PASSWORD and CADDY_DEMO_PASSWORD_HASH
docker compose up -d transflow-core
# Starts transflow-core + all its dependencies (Postgres, Kafka, Temporal, Elasticsearch)
# Skips Caddy — not needed locally; access the app directly at http://localhost:8080
# Wait ~2 minutes for Temporal + Elasticsearch to initialise before the app is fully ready
```

## Ops Notes

### GHCR authentication

The server must be authenticated to pull from `ghcr.io/raphaelplee/transflow-core`. Making the
package public is fragile — GitHub recreates it as private whenever a new image is pushed after
the package entry is deleted. Use credentials instead:

```bash
# One-time setup on the server — stored in ~/.docker/config.json and persists across reboots.
# Generate a classic PAT at github.com/settings/tokens with read:packages scope only.
echo '<PAT>' | docker login ghcr.io -u raphaelplee --password-stdin
```

### Compose secrets

`compose/.env` holds two values. Generate the Caddy password hash with:

```bash
docker run --rm httpd:alpine htpasswd -nbBC 14 demo demo | cut -d: -f2
```

### Adminer

Adminer is in the stack but not routed by Caddy — reach it through a tunnel:

```bash
ssh -L 5050:adminer:8080 root@raphaellee.de
```

### JVM memory in containers

Docker's `mem_limit` is a cgroup hard limit, but JVM ergonomics size the heap from host RAM
by default — not the container limit. Always pair `mem_limit` with an explicit `-Xmx` in
`JAVA_OPTS`. Rule of thumb: `-Xmx` at ~80% of `mem_limit`, leaving headroom for metaspace,
threads, and NIO buffers.

| Service | mem_limit | JAVA_OPTS |
|---------|-----------|-----------|
| transflow-core | 512m | via `JAVA_OPTS` env var |
| elasticsearch | 1g | `-Xms256m -Xmx768m` |
| kafka-ui | 400m | `-Xms64m -Xmx320m` |

### Concurrent deploys race on the server

The `deploy` job in `.github/workflows/ci.yml` has no `concurrency` group, so two merges
to `main` in quick succession run `docker compose up -d` on the server at the same time.
Observed on 2026-06-13 ([run 27467218920](https://github.com/raphaelplee/labs/actions/runs/27467218920)),
when three Dependabot PRs merged within ten seconds: one deploy recreated `transflow-core`
while another was mid-recreate, and the second failed with

```
Container badc0508b040_compose-transflow-core-1 Started
Container compose-transflow-core-1 Starting
Error response from daemon: No such container: badc0508b040...
```

The stack was left healthy — the first deploy had already started the new container — and
the next merge deployed cleanly. So this is a red CI run, not an outage. Fix is one key on
the job (see [Open items](#open-items)); until then, avoid merging two PRs to `main` at once.

### Kafka topic poison messages

If a consumer loops on the same offset with deserialization errors, the topic has a poisoned
message at that offset. The entire consumer group is blocked until it is cleared.
Fix: delete and recreate the topic (acceptable in dev; the DLQ under [Open items](#open-items) is the production fix).

```bash
docker exec compose-kafka-1 /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --delete --topic <topic-name>
docker compose restart kafka-init  # recreates the topic
```

### Open items

The four things this service does not do yet. Everything else on this page describes shipped
behaviour.

- **No Kafka DLQ** — a poisoned offset blocks its consumer group until the topic is wiped
  (see [Kafka topic poison messages](#kafka-topic-poison-messages)). The fix is a
  `DefaultErrorHandler` with a `DeadLetterPublishingRecoverer`, plus `*.dlq` topics in `kafka-init`.
- **The deploy job has no `concurrency` group** — two merges seconds apart collide
  (see [Concurrent deploys race on the server](#concurrent-deploys-race-on-the-server)).
- **The demo is unauthenticated** — anyone can POST to the saga endpoints. Keycloak was
  specified in May 2026 and never built; the design record is in [DESIGN.md](DESIGN.md).
- **The dashboard polls `/api/sagas` every 2 s** instead of streaming — SSE/HTMX is designed
  but not built; see [DESIGN.md](DESIGN.md).
