# Raphael Lee — transflow

Live subscription-lifecycle saga: a Spring Boot 4 modular monolith driving a Temporal
workflow through Kafka, backed by Postgres, served from Hetzner behind Caddy.

| Surface | URL |
|---------|-----|
| Demo + Swagger | [transflow.raphaellee.de](https://transflow.raphaellee.de) (Swagger at `/swagger-ui/index.html`) |
| Temporal UI | [temporal.raphaellee.de](https://temporal.raphaellee.de) — Caddy `basic_auth` |
| Kafka UI | [kafka.raphaellee.de](https://kafka.raphaellee.de) — Caddy `basic_auth` |

`raphaellee.de` redirects to the demo. `raphaellee.my` and `raphaellee.eu` — including
their `transflow.` and `dashboard.` subdomains — answer 301 to the `.de` equivalent.
`dashboard.raphaellee.de` returns `coming soon` until a metrics dashboard exists.

## Layout

```
compose/       Production stack — Caddy routes + docker-compose services
event-driven/  The service — source, Dockerfile, schema migrations, design notes
.github/       CI: test → build image → push to GHCR → deploy over SSH
```

## How it ships

A push to `main` runs `CI`: `mvn test`, then `mvn -f event-driven/pom.xml package`, then
`docker/build-push-action` publishes `ghcr.io/raphaelplee/transflow-core:main`, then the
`deploy` job SSHs to the server and runs `git pull && docker compose up -d` in
`/opt/labs/compose`. `Integration Tests` runs `mvn verify` in `event-driven/` on every
push and pull request to `main`. Dependabot keeps the pinned action SHAs moving.

## Operations

- **Only Caddy publishes ports** (80, 443, 443/udp). Docker bypasses UFW on the host, so
  adding a `ports:` mapping to any other service exposes it to the internet immediately.
- `compose/.env` carries `POSTGRES_PASSWORD` and `CADDY_DEMO_PASSWORD_HASH`. It is
  gitignored and exists only on the server; `compose/.env.example` is the template.
- `docker compose pull transflow-core && docker compose up -d` is the whole deploy. The
  database and broker volumes survive it.
- Login to the UIs is the single `demo` user; rotate it by rehashing and restarting Caddy.

## Known gaps

- **No Kafka DLQ.** The consumer uses `ByteArrayDeserializer`, so failures surface in the
  Spring conversion layer rather than the client; a poisoned offset currently blocks its
  consumer group until the topics are wiped. Fix belongs in a `DefaultErrorHandler` with
  a `DeadLetterPublishingRecoverer` (`*.dlq` topics must be added to `kafka-init`).
- **The demo is unauthenticated.** Keycloak was specified in May 2026 and never built —
  `event-driven/DESIGN.md` keeps the design record.
- **The deploy job has no concurrency group**, so two merges seconds apart run
  `docker compose up -d` against the same server concurrently.
- **The saga dashboard polls** `/api/sagas` every 2 s; SSE is the deferred improvement.

Historical design records, module stubs and planning documents from the 2026 portfolio
build were removed on 2026-10-25 and remain available at tag
`archive/pre-trim-2026-10-25`.
