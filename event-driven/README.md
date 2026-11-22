# transflow-core

Subscription lifecycle saga — order → payment → fulfillment — Temporal, Kafka and Spring Modulith
in one JVM.

[Demo](https://transflow.raphaellee.de) ·
[Swagger](https://transflow.raphaellee.de/swagger-ui/index.html) ·
[Temporal UI](https://temporal.raphaellee.de) ·
[Kafka UI](https://kafka.raphaellee.de)

```
AWAITING_PAYMENT
  ├── paymentOk        → FULFILLMENT_PROCESSING
  │     ├── fulfillmentDone → COMPLETED
  │     └── timeout         → TIMED_OUT
  ├── paymentFailed    → PAYMENT_FAILED
  └── timeout          → PAYMENT_TIMED_OUT
```

`/api/sagas` returns these names verbatim. Why that costs a query per workflow is in
[DESIGN.md](DESIGN.md). The fulfillment deadline is `SAGA_FULFILLMENT_TIMEOUT_SECONDS` (30 s),
the payment deadline `SAGA_PAYMENT_TIMEOUT_SECONDS` (600 s).

## Kafka topics

| Topic | Produced by | Consumed by |
|---|---|---|
| `order.created` | order | orchestration |
| `payment.processed` | payment | orchestration, fulfillment |
| `payment.failed` | payment | orchestration |
| `fulfillment.completed` | fulfillment | orchestration |

The payload of each topic is the `@Externalized` record beside its event, written as raw JSON
with no type headers. A record that still fails after five retries with exponential back-off
goes to `<topic>-dlt`. Module dependencies are the `package-info.java` declarations, checked by
`ModularityTest`.

## Running locally

```bash
mvn -f event-driven/pom.xml package -DskipTests
cd compose
cp .env.example .env
docker compose -f docker-compose.yml -f compose.local.yml up -d --build transflow-core
```

The app answers on `http://localhost:8080`. Postgres, Kafka, Temporal and Elasticsearch come up
with it and need about two minutes; Caddy is skipped. `compose.local.yml` builds the image from
the JAR and publishes the port; it is not named `compose.override.yml` because Compose would then
load it on the server too.

## Ops Notes

**GHCR** — the server authenticates to `ghcr.io/raphaelplee/transflow-core` with a classic PAT
scoped to `read:packages`. The package must stay private: GitHub recreates it private after the
next push if the entry is deleted.

**Secrets** — `compose/.env` holds `POSTGRES_PASSWORD` and the Caddy password hash. Single-quote
the hash: Compose expands `$name` inside unquoted `.env` values, and a bcrypt hash is full of `$`.
The password must match the `data-p` hint in `index.html`:

```bash
docker run --rm httpd:alpine htpasswd -nbBC 14 demo '<password>' | cut -d: -f2
```

**Admin UIs** — the demo password is public, so Kafka UI runs with `KAFKA_CLUSTERS_0_READONLY`
and Temporal UI with `TEMPORAL_DISABLE_WRITE_ACTIONS`. Write operations go through the server.

**Adminer** — published on the server's loopback only; reach it with
`ssh -L 5050:localhost:5050 root@raphaellee.de` and open `http://localhost:5050`.

**Memory** — `mem_limit` is a cgroup limit, and a JVM inside it defaults to a heap of 25% of
that limit. Every JVM service therefore sets an explicit heap at roughly 80% of its limit:
transflow-core 512m (`-Xmx400m`), elasticsearch 1g (`-Xms256m -Xmx768m`), kafka 1g
(`KAFKA_HEAP_OPTS=-Xms256m -Xmx768m`; without it `kafka-server-start.sh` takes `-Xmx1G -Xms1G`),
kafka-init 128m (`-Xmx64m`), kafka-ui 400m (`-Xms64m -Xmx320m`).

**Deploy** — runs only after `mvn verify` (unit and integration tests) passes, and ships the JAR
that was tested. The runner connects with plain OpenSSH and `StrictHostKeyChecking=yes`. Each
line of the `DEPLOY_HOST_KEY` secret is rewritten to the alias `deploy-host`, so its host field
(name, IP or hashed) does not matter. The server checks out the pushed commit detached and runs
the `sha-<commit>` image, so `git -C /opt/labs rev-parse HEAD` is what is live. To roll back,
re-run the deploy job of an older run. `docker compose pull` refreshes every image, not only
transflow-core. `up --wait` cannot cover the whole file because `kafka-init` is a one-shot that
exits 0, so the wait is scoped to transflow-core; its healthcheck probes port 8080 with bash
`/dev/tcp` because the JRE image has no curl or wget. Caddy's directory is bind-mounted, not the
file: `git checkout` replaces the Caddyfile with a new inode, which a single-file mount never
sees, so the closing `caddy reload` would reload the old config. The `deploy-production`
concurrency group keeps one deploy running and one pending; a newer push replaces the pending
one. The remote script starts with `set -e`, so the first failing command fails the run.

**Dead letters** — after five retries a record is published to `<topic>-dlt` and its group moves
on. Inspect the record and its exception headers in Kafka UI.

### Open items

- The demo is unauthenticated; Keycloak was designed and never built ([DESIGN.md](DESIGN.md)).
- The dashboard polls `/api/sagas` every 2 s instead of streaming, and each poll queries every
  listed workflow. A `SagaStatus` search attribute would remove the queries.
- `payments.order_id` has no unique constraint yet. The code rejects repeats; the constraint
  waits for a check that production has no duplicates:
  `SELECT order_id, count(*) FROM transflow.payments GROUP BY 1 HAVING count(*) > 1;`
- Sagas started before the payment deadline existed never time out. Terminate them, then remove
  the `payment-timeout` version branch ([DESIGN.md](DESIGN.md)).
- Spring Boot 4.0 open-source support ends 2026-12-31; Dependabot now proposes Maven updates.
- `temporalio/auto-setup` is a development image with no release after 1.29.x.
- The deploy logs in as root and the app uses the `postgres` superuser.
