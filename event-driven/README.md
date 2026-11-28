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
  └── paymentFailed    → PAYMENT_FAILED
```

`/api/sagas` returns these names verbatim. Why that costs a query per workflow is in
[DESIGN.md](DESIGN.md).

## Kafka topics

| Topic | Produced by | Consumed by |
|---|---|---|
| `order.created` | order | orchestration |
| `payment.processed` | payment | orchestration, fulfillment |
| `payment.failed` | payment | orchestration |
| `fulfillment.completed` | fulfillment | nothing — the database is the audit record |

The payload of each topic is the `@Externalized` record beside its event, written as raw JSON
with no type headers. Module dependencies are the `package-info.java` declarations, checked by
`ArchUnitTest`.

## Running locally

```bash
cd compose
cp .env.example .env
docker compose up -d transflow-core
```

The app answers on `http://localhost:8080`. Postgres, Kafka, Temporal and Elasticsearch come up
with it and need about two minutes; Caddy is skipped.

## Ops Notes

**GHCR** — the server authenticates to `ghcr.io/raphaelplee/transflow-core` with a classic PAT
scoped to `read:packages`. The package must stay private: GitHub recreates it private after the
next push if the entry is deleted.

**Secrets** — `compose/.env` holds `POSTGRES_PASSWORD` and the Caddy password hash for the
shared `demo` login. Make a hash from `compose/` with:

```bash
docker compose run --rm --no-deps caddy caddy hash-password
```

**`/demo/` logins** — `/demo/` takes one login per person, not the shared `demo` login. Each
person is a file `compose/caddy/demo-users/<username>`, mode `0600`, holding `<username> <hash>`;
the directory is gitignored and must hold nothing else, since Caddy imports every file in it,
editor backups included. With no file, every request gets 401. From `compose/`, with a hash made
as in **Secrets**:

```bash
mkdir -p caddy/demo-users && install -m 600 /dev/null caddy/demo-users/<username>
```

```bash
printf '%s %s\n' '<username>' '<hash>' > caddy/demo-users/<username>
```

Then reload Caddy with the deploy's last command in [`ci.yml`](../.github/workflows/ci.yml). To
end a login, delete the file and reload; it stops working at once. A duplicate username or
malformed line fails the reload and Caddy keeps the running config, but on its next start Caddy
exits and every site goes down, so fix the file until the reload succeeds. Every `/demo` request,
refused ones included, goes to `/var/log/caddy/demo.log` (volume `caddy_logs`) as JSON with
`user_id`; Caddy redacts `Authorization` and `Cookie`. The file rolls daily at 00:00 UTC, and
each rolled file is deleted 7 days later, enough to look into an incident while keeping IPs no
longer than needed (GDPR Art. 5(1)(e)):

```bash
docker compose exec caddy cat /var/log/caddy/demo.log
```

**Adminer** — not routed by Caddy; reach it with `ssh -L 5050:adminer:8080 root@raphaellee.de`.

**Memory** — `mem_limit` is a cgroup limit and the JVM still sizes its heap from host RAM, so
every service pairs it with an explicit `-Xmx` at roughly 80% of the limit: transflow-core 512m,
elasticsearch 1g (`-Xms256m -Xmx768m`), kafka-ui 400m (`-Xms64m -Xmx320m`).

**Deploy** — the run is red unless `transflow-core` becomes healthy. `up --wait` cannot cover
the whole file because `kafka-init` is a one-shot that exits 0, so the wait is scoped to that
service; the healthcheck probes port 8080 with bash `/dev/tcp` because the JRE image has no curl
or wget. Caddy is reloaded explicitly at the end — `up -d` never reloads a changed `Caddyfile`.
Its directory is mounted, not the file: `git pull` writes a changed file as a new inode, which a
single-file bind mount never sees (moby/moby#15793). CI validates the `Caddyfile` through the
compose service, with a placeholder password hash and user file, so a broken one fails before the
deploy on the pinned image production runs. The deploy validates it again with the server's user
files before `up -d`, which may restart Caddy. labs creates the `labs_demo` network, so it deploys before the demo
in raphaelplee/labs-private; the demo joins it as `external` and serves `demo:8000`, and only Caddy
shares it. Caddy strips `/demo` and sends `X-Forwarded-Prefix: /demo` and the signed-in user as
`X-Remote-User`; only Caddy can set them, since only Caddy shares the network. The name is fixed, not `compose_*`, because another repo depends on it. A `deploy-production` concurrency
group queues concurrent deploys instead of interleaving them. The remote script starts with
`set -e`, so the first failing command fails the run — the action does not fail the step on a
remote error by itself.

**Poisoned offsets** — a consumer looping on one offset blocks its group until the topic is
recreated:

```bash
docker exec compose-kafka-1 /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --delete --topic <topic>
docker compose restart kafka-init
```

### Open items

- No Kafka DLQ — needs a `DefaultErrorHandler` with `DeadLetterPublishingRecoverer` and `*.dlq`
  topics in `kafka-init`.
- The demo is unauthenticated; Keycloak was designed and never built ([DESIGN.md](DESIGN.md)).
- The dashboard polls `/api/sagas` every 2 s instead of streaming.
- The `saga-` workflow-ID prefix is written in five places instead of one.
