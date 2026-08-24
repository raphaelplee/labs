# TODOS

Deferred items from the `/plan-ceo-review` session on 2026-05-14.
Each item re-verified against the code, `compose/`, and the GitHub Actions
workflows on 2026-08-24 — dependencies below reflect what is actually live, not
the state assumed when the item was written.

---

## P2

### Kafka Dead Letter Queue (DLQ) for deserialization failures

**What:** Add Kafka DLQ configuration to the `event-driven` module. Events that fail
deserialization should be routed to a `*.dlq` topic rather than causing indefinite
retry loops.

**Why:** Without a DLQ, a single unreadable message at offset 0 blocks all processing
for the consumer group indefinitely — experienced first-hand during ops: a serializer
mismatch caused the consumer to loop on the same poisoned offset until topics were
manually wiped. DLQ is standard Kafka production practice; its absence is an obvious
omission for any senior engineer reviewing the module.

**Where to start:** the consumer currently uses `ByteArrayDeserializer` (`application.yml`)
with a `ByteArrayJacksonJsonMessageConverter` `@Bean` (`KafkaConfig`), so the Kafka-client
layer never fails — bad payloads blow up in the Spring conversion layer instead. The fix
therefore belongs in the error handler, not the deserializer:

```java
// KafkaConfig — DeadLetterPublishingRecoverer routes failed records to <topic>.dlq
@Bean
DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> template) {
    return new DefaultErrorHandler(
        new DeadLetterPublishingRecoverer(template,
            (record, ex) -> new TopicPartition(record.topic() + ".dlq", record.partition())),
        new FixedBackOff(1000L, 2));
}
```

Spring Boot 4 wires a single `CommonErrorHandler` bean into the auto-configured listener
factory. The four `*.dlq` topics need adding to the `kafka-init` service in
`compose/docker-compose.yml` (`AUTO_CREATE_TOPICS_ENABLE` is `false`).

**Effort:** S (human ~30 min / CC ~5 min) | **Priority:** P2 | **Depends on:** nothing — ready now

---

### ADR directory (`docs/adr/`)

**What:** Create `docs/adr/` with 4-5 Architecture Decision Records:
- `0001-temporal-over-kafka-streams.md` — why Temporal for saga orchestration
- `0002-spring-modulith-over-microservices.md` — 2026 modular monolith rationale
- `0003-kraft-kafka-over-managed.md` — KRaft vs MSK/Confluent for this use case
- `0004-ec2-first-over-eks.md` — why EC2 baseline before EKS for multicloud
- `0005-ollama-over-copilot-cli.md` — why Ollama has a stable API; Copilot CLI does not

**Why:** Each ADR converts an implementation decision into an interview talking point
with a written artifact behind it. "I made this choice because X" is stronger when
there's a 1-page markdown file in the repo to point to.

**Context:** Deferred from the CEO plan cherry-pick ceremony. Build after the code
exists so ADRs document proven decisions, not aspirational ones. The first three
decisions are already shipped and provable in `event-driven/`; 0004 and 0005 remain
aspirational until `multicloud` and `ai-augmented-cicd` are built. Follow the format
already used by the `decisions/` directory in the private notes repo (numbered,
Context / Decision / Why / Consequences).

**Effort:** S (human ~2-3 hrs / CC ~20 min) | **Priority:** P2 | **Depends on:** nothing for 0001-0003

---

## P3 — Nice to have

### GitHub Actions badges + AI metrics badge

**What:** Add to main README:
1. GitHub Actions CI build status badge (standard `shields.io` from GitHub)
2. Test coverage badge (JaCoCo report via `jacoco.github.io`)
3. AI review acceptance rate badge (custom: HTTP endpoint returning current week's
   rate from `pr_metrics` table, formatted as `shields.io` endpoint badge)

**Why:** Visual credibility signals in 3 seconds before a reviewer reads a line of code.

**Context:** Deferred from cherry-pick ceremony. Badge 1 is unblocked — the `CI` and
`Integration Tests` workflows are both live and green on `main`, and GitHub already
publishes a badge URL for each. Badge 2 needs the JaCoCo plugin, which is not in either
`pom.xml` yet. Badge 3 still needs the metrics endpoint from `ai-augmented-cicd`.

**Effort:** S (human ~30 min / CC ~5 min) | **Priority:** P3 | **Depends on:** badge 1 ready now; badge 2 on JaCoCo; badge 3 on the metrics endpoint

---

### Grafana collection health alert

**What:** Add one Grafana alert rule to the `dashboard.raphaellee.de` dashboard:
- Trigger: `count(collection_status = 'failed') over last 2 hours > 1`
- Notifies (email or webhook) when the pr_metrics SSH tunnel has been failing

**Why:** Prevents discovering the metrics system is broken during interview prep.
The `collection_status` column tracks whether each write attempt succeeded, but
without an alert you'd only notice failures when reviewing the chart and seeing
unexplained gaps.

**Context:** Added from `/plan-eng-review`. Classic "observe the observer" pattern.
Trivial alongside the Grafana dashboard build.

**Where to start:** Grafana alert rule in the dashboard JSON (one rule, one
notification channel). Requires the `collection_status` column to exist in `pr_metrics`.

**Effort:** S (human ~15 min / CC ~2 min) | **Priority:** P3 | **Depends on:** the Grafana dashboard + `pr_metrics` schema, neither of which exists yet — `dashboard.raphaellee.de` currently serves a Caddy "coming soon" response

---

### Server-Sent Events for the saga dashboard (stretch)

**What:** Replace the dashboard's 2-second `/api/sagas` poll with a push stream:
- SSE endpoint at `/events/stream` in the `orchestration` module
- `index.html` subscribes and re-renders on each saga state transition

**Why:** The saga state machine currently animates on a poll tick, so transitions can
lag by up to 2s and every idle browser tab keeps hitting the API. A push stream makes
the state machine visibly react the instant the workflow signals, which is the moment
the demo is selling.

**Context:** Deferred from Section 11 review, where it was framed as "add a frontend" —
that half already shipped: `event-driven/src/main/resources/static/index.html` serves a
step-by-step saga dashboard at `transflow.raphaellee.de`. What remains is the transport.
HTMX is optional; `EventSource` plus the existing `renderSagas()` is enough.

**Where to start:** an `SseEmitter` (or `Flux<ServerSentEvent>`) endpoint fed from the
same query path `SagaController` already uses; keep the poll as the fallback for
browsers that drop the connection.

**Effort:** M (human ~1 weekend / CC ~2 hrs) | **Priority:** P3 | **Depends on:** nothing — ready now

---

### Docker port exposure policy

**What:** Document which ports are intentionally published from `compose/docker-compose.yml`
and which must never be, in `event-driven/README.md` under Ops Notes:
- Intentionally published: 80 (Caddy HTTP), 443 + 443/udp (Caddy HTTPS)
- Never published: 5432 (Postgres), 9092/9093 (Kafka), 7233 (Temporal server), 8233
  (Temporal UI), 8090 (Kafka UI), 9200 (Elasticsearch), 8080 (Spring Boot app — reached
  through Caddy only)

**Why:** Docker bypasses UFW on Linux — any `ports:` mapping in the compose file is
immediately internet-accessible. Without a written policy, a future service addition may
accidentally expose a management API to the public internet.

**Context:** Identified during the Weekend 1 eng review. The server runs Docker with UFW
enabled; UFW protects host-level SSH but does not protect Docker-published ports. The
current compose file is already correct — `caddy` is the only service with a `ports:`
block, everything else is reachable only on the compose network — so this is about
recording the rule, not changing behaviour.

**Effort:** XS (human ~5 min / CC ~1 min) | **Priority:** P3 | **Depends on:** nothing — ready now
