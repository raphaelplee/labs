# transflow

Subscription-lifecycle saga — a Spring Boot 4 modular monolith orchestrating a Temporal
workflow through Kafka, on Postgres, served from Hetzner behind Caddy.

[Demo](https://transflow.raphaellee.de) ·
[Swagger](https://transflow.raphaellee.de/swagger-ui/index.html) ·
[Temporal UI](https://temporal.raphaellee.de) ·
[Kafka UI](https://kafka.raphaellee.de) — both UIs behind Caddy `basic_auth`, user `demo`

```
compose/       Caddy routes + the docker-compose stack
event-driven/  The service — source, Dockerfile, migrations, design notes
.github/       CI: test → image → GHCR → SSH deploy
```

Push to `main` → `mvn test` → image `ghcr.io/raphaelplee/transflow-core:main` → the `deploy`
job SSHs to `/opt/labs` and runs `git pull && docker compose up -d`. `Integration Tests` runs
`mvn verify` on every pull request.

**Only Caddy publishes ports** (80, 443, 443/udp). Docker bypasses UFW, so a `ports:` mapping
on any other service is public the moment it lands. Secrets live in `compose/.env` on the
server only; `compose/.env.example` is the template.

**Known gaps.** No Kafka DLQ — a poisoned offset blocks its consumer group until the topic is
wiped · the demo is unauthenticated (Keycloak was designed, never built — see
`event-driven/DESIGN.md`) · the deploy job has no concurrency group · the dashboard polls
`/api/sagas` every 2 s instead of streaming.

`raphaellee.my` and `raphaellee.eu` 301 to `.de`; `dashboard.raphaellee.de` answers
"coming soon". Everything removed on 2026-10-25 — module stubs, planning docs, design
records — is at tag `archive/pre-trim-2026-10-25`.
