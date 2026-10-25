# transflow

Subscription-lifecycle saga — a Spring Boot 4 modular monolith orchestrating a Temporal
workflow through Kafka, on Postgres, served from Hetzner behind Caddy.

[Demo](https://transflow.raphaellee.de) ·
[Swagger](https://transflow.raphaellee.de/swagger-ui/index.html) ·
[Temporal UI](https://temporal.raphaellee.de) ·
[Kafka UI](https://kafka.raphaellee.de)

Where things live — each fact is written once, in the file named here:

- [`compose/`](compose/) — the stack that runs: Caddy routes, services, `.env.example`
- [`event-driven/`](event-driven/) — the service: source, migrations,
  [design record](event-driven/DESIGN.md), [ops notes and open items](event-driven/README.md#ops-notes)
- [`.github/workflows/`](.github/workflows/) — how a push reaches the server

`raphaellee.my`, `raphaellee.eu` and their subdomains are redirects; the
[Caddyfile](compose/Caddyfile) is the only place they are defined.

Everything removed on 2026-10-25 is at tag `archive/pre-trim-2026-10-25`.
