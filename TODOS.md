# TODOS

Deferred items from the /plan-ceo-review session on 2026-05-14.

---

## P2 — Do soon after Weekend 3

### Full DESIGN.md with design system documentation

**Partially complete (2026-05-20):** Color token system (20 tokens), component table (14 components including new step-by-step elements), auth strategy, blurb copy, and typography rationale are now documented. Blurb text corrected (authorization_code flow, not Resource Server).

**What remains:** Add a spacing/sizing section, document the pulse keyframe animation intent, add a "decisions deferred" section for known design debt (mobile layout, full Keycloak theme, HTMX rewrite).

**Why:** Future UI additions (Weekend 3 toasts, auth nav bar) need the spacing reference to stay consistent without reverse-engineering the CSS.

**Where to start:** `event-driven/DESIGN.md` — add `## Spacing & Sizing` section after the Typography section.

**Effort:** XS (human ~15 min / CC ~5 min) | **Priority:** P2 | **Depends on:** Weekend 3 (auth PR) complete

---

## P2 — Do soon after Weekend 5

### Kafka Dead Letter Queue (DLQ) for deserialization failures

**What:** Add Kafka DLQ configuration to the `event-driven` module. Events that fail
deserialization should be routed to a `*.dlq` topic rather than causing indefinite
retry loops.

**Why:** Without a DLQ, a single unreadable message at offset 0 blocks all processing
for the consumer group indefinitely — experienced first-hand during ops: a serializer
mismatch caused the consumer to loop on the same poisoned offset until topics were
manually wiped. DLQ is standard Kafka production practice; its absence is an obvious
omission for any senior engineer reviewing the module.

**Where to start:** `application.yml` consumer config — wrap `JsonDeserializer` with
Spring Kafka's `ErrorHandlingDeserializer` (delegates to `JsonDeserializer`, routes
failures to DLQ instead of crashing the container):
```yaml
spring.kafka.consumer:
  value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
  properties:
    spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
```
Then add a `DeadLetterPublishingRecoverer` bean and wire it into the `DefaultErrorHandler`
on the `KafkaListenerContainerFactory`.

**Effort:** S (human ~30 min / CC ~5 min) | **Priority:** P2 | **Depends on:** Weekend 2

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
exists so ADRs document proven decisions, not aspirational ones.

**Effort:** S (human ~2-3 hrs / CC ~20 min) | **Priority:** P2 | **Depends on:** Weekend 5 complete

---

## P3 — Nice to have

### GitHub Actions badges + AI metrics badge

**What:** Add to main README:
1. GitHub Actions CI build status badge (standard `shields.io` from GitHub)
2. Test coverage badge (JaCoCo report via `jacoco.github.io`)
3. AI review acceptance rate badge (custom: HTTP endpoint returning current week's
   rate from `pr_metrics` table, formatted as `shields.io` endpoint badge)

**Why:** Visual credibility signals in 3 seconds before a reviewer reads a line of code.

**Context:** Deferred from cherry-pick ceremony. Requires both the CI pipeline
(Weekend 4) and the Grafana metrics endpoint (Weekend 5) to be live.

**Effort:** S (human ~30 min / CC ~5 min) | **Priority:** P3 | **Depends on:** Weekend 5 complete

---

### Grafana collection health alert

**What:** Add one Grafana alert rule to the `cicd.raphaellee.de` dashboard:
- Trigger: `count(collection_status = 'failed') over last 2 hours > 1`
- Notifies (email or webhook) when the pr_metrics SSH tunnel has been failing

**Why:** Prevents discovering the metrics system is broken during interview prep.
The `collection_status` column (added in Weekend 5) tracks whether each write
attempt succeeded, but without an alert you'd only notice failures when reviewing
the chart and seeing unexplained gaps.

**Context:** Added from /plan-eng-review. Classic "observe the observer" pattern.
Trivial alongside the Grafana dashboard build.

**Where to start:** Grafana alert rule in the cicd dashboard JSON (one rule, one
notification channel). Requires `collection_status` column to exist in pr_metrics.

**Effort:** S (human ~15 min / CC ~2 min) | **Priority:** P3 | **Depends on:** Weekend 5 (Grafana dashboard + collection_status column)

---

### HTMX frontend for event-driven (stretch)

**What:** A minimal HTMX frontend at `event-driven.raphaellee.de`:
- Subscription status page: list active subscriptions
- Payment event feed: real-time saga state updates via Server-Sent Events
- Shows the saga state machine executing visually

**Why:** Makes the demo visually real for non-technical stakeholders (CTOs, product
managers in the interview loop). The core audience (engineers) is fine with curl, but
a live visual makes the "here's my saga working" moment stronger.

**Context:** Deferred from Section 11 review. API-only is correct for Weekends 1-5.
HTMX is a 1-weekend addition after the core is solid.

**Where to start:** `event-driven/src/main/resources/templates/` — add Thymeleaf +
HTMX dependency; Server-Sent Events endpoint at `/events/stream`.

**Effort:** M (human ~1 weekend / CC ~2 hrs) | **Priority:** P3 | **Depends on:** Weekend 5 complete

---

### Docker port exposure policy comment

**What:** Add a comment block at the top of `compose/docker-compose.yml` documenting which ports are intentionally mapped vs which must never be mapped:
- Intentionally exposed: 80 (Caddy HTTP), 443 (Caddy HTTPS)
- Never expose: 5432 (Postgres), 9092 (Kafka), 7233 (Temporal server), 8080 (Spring Boot app — internal only)

**Why:** Docker bypasses UFW on Linux — any `ports:` mapping in the compose file is immediately internet-accessible. Without a policy comment, Weekend 2+ additions of Kafka and Temporal may accidentally expose management APIs to the public internet.

**Context:** Identified during Weekend 1 eng review. The CX33 server runs Docker with UFW enabled; UFW protects host-level SSH but does not protect Docker-mapped ports. The compose file must be the source of truth for what is and isn't exposed.

**Where to start:** `compose/docker-compose.yml` — add a comment block at the top, before the `services:` key.

**Effort:** XS (human ~5 min / CC ~1 min) | **Priority:** P3 | **Depends on:** Weekend 1 complete
