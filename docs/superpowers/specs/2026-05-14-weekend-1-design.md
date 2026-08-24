# Weekend 1 Design Spec
Generated on 2026-05-14
Branch: main | Repo: raphaelplee/labs

> **Status: SHIPPED — historical record, not current documentation.**
> Kept as-written; where it disagrees with the repo, the repo wins. Known drift:
> the CI workflow runs a bare `mvn test`, not `mvn test -P skip-integration-tests`.

---

## Scope

Weekend 1 delivers five things:

1. Root README — portfolio landing page readable in 60 seconds
2. 8 module stub READMEs — opinionated stubs with architectural decisions
3. Spring Boot 4.x BOM — Java 25, Jakarta EE 11, dependency compat validated
4. CI workflow — `mvn test -P skip-integration-tests` on push/PR
5. Hetzner CX33 — Docker Compose + Caddy auto-TLS + DNS (transflow + dashboard subdomains)

**Approach: repo-first, infra second.** Repo scaffold (PRs 1–5) has zero external dependencies. Hetzner provisioning + DNS propagation runs in parallel or after, no blocking dependency on the code track.

---

## Section 1: Repo Scaffold

### PR Strategy (3x baseline)

| PR | Content |
|----|---------|
| 1 | Root `README.md` |
| 2 | 8 module stub READMEs |
| 3 | Root `pom.xml` — Spring Boot 4.x BOM |
| 4 | `.github/workflows/ci.yml` |
| 5 | `.devcontainer/devcontainer.json` |

5 PRs from Weekend 1 establishes the 3x baseline dataset before Weekend 4 AI review goes live.

### Root README

Structure (60-second readable for a hiring manager):

```
# Raphael Lee — Engineering Portfolio

<one-liner: what this demonstrates>

## Modules

| Module | Status | Stack |
|--------|--------|-------|
| event-driven | stub | Spring Boot 4, Kafka, Temporal, Postgres |
| ai-augmented-cicd | stub | TypeScript, GitHub Actions, Claude |
| multicloud | stub | AWS + Hetzner, Terraform |
| observability | stub | Grafana, Prometheus |
| api-design | stub | ... |
| platform-engineering | stub | ... |
| iot | stub | ... |
| migration-patterns | stub | ... |

## Stack
Java 25 · Spring Boot 4 · Kafka · Temporal · Postgres · Hetzner · AI-augmented CI/CD

## Live
- [transflow.raphaellee.de](https://transflow.raphaellee.de) — subscription saga demo
- [dashboard.raphaellee.de](https://dashboard.raphaellee.de) — CI/CD metrics
```

Status values progress: `stub → in-progress → live` as weekends complete.

### 8 Module Stub READMEs

Every stub follows the accepted template — four required elements:

1. **Architectural decision** — one concrete choice made and why
2. **Trade-off** — what was given up to make that choice
3. **NOT in scope** — explicit boundary
4. **Reference** — one link (paper, talk, or tool)

Module list and pre-filled decisions:

| Module | Decision | Trade-off | NOT in scope | Reference |
|--------|----------|-----------|--------------|-----------|
| `event-driven` | Temporal for saga orchestration over Kafka Streams | Temporal adds an infra component; Kafka Streams is zero-overhead | Multi-region Temporal | [Temporal docs](https://docs.temporal.io) |
| `ai-augmented-cicd` | Ollama as local-dev backend; Claude in CI | Ollama output quality varies vs Claude; two code paths | Copilot CLI (no stable API) | [Anthropic API docs](https://docs.anthropic.com) |
| `multicloud` | EC2-based Docker Compose before EKS | EKS adds k8s complexity before value is proven | Managed Kafka/RDS | [Terraform docs](https://developer.hashicorp.com/terraform) |
| `observability` | Grafana + Postgres as metrics store | Not a time-series DB; Postgres is good enough for PR-level metrics | Full APM / distributed tracing | [Grafana docs](https://grafana.com/docs) |
| `api-design` | TBD Weekend 7+ | — | — | — |
| `platform-engineering` | TBD Weekend 7+ | — | — | — |
| `iot` | TBD Weekend 7+ | — | — | — |
| `migration-patterns` | TBD Weekend 7+ | — | — | — |

The four deferred modules get placeholder stubs now; decisions filled in when built.

### Spring Boot BOM

**Stack:** Java 25 · Spring Boot 4.x latest stable · Jakarta EE 11

**Compatibility gate — verify before committing pom.xml (PR 3):**

| Dependency | Check | Pass condition |
|------------|-------|----------------|
| Temporal Java SDK | Check [temporal.io releases](https://github.com/temporalio/sdk-java/releases) for Spring Boot 4 / Jakarta EE 11 mention | Release notes or issues confirm `jakarta.*` namespace |
| `spring-kafka` | Check [Spring for Apache Kafka releases](https://github.com/spring-projects/spring-kafka/releases) | Version compatible with Spring Boot 4 listed |
| Flyway | Check [Flyway release notes](https://github.com/flyway/flyway/releases) for Flyway 10.x | Jakarta EE 11 confirmed |

If any dependency is incompatible: open a GitHub issue in this repo tracking the blocker, pin the last compatible Spring Boot version instead, and note the deviation in `event-driven/README.md`. Do NOT start Weekend 2 coding without passing this gate.

Root `pom.xml` structure:
```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version><!-- latest Spring Boot 4.x stable --></version>
</parent>

<properties>
  <java.version>25</java.version>
</properties>

<modules>
  <module>event-driven</module>
</modules>
```

`event-driven/pom.xml`: child module, no source yet. Validates the module structure compiles.

Profile `skip-integration-tests`:
```xml
<profile>
  <id>skip-integration-tests</id>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
          <excludedGroups>integration</excludedGroups>
        </configuration>
      </plugin>
    </plugins>
  </build>
</profile>
```

### CI Workflow

`.github/workflows/ci.yml`:
```yaml
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
      - run: mvn test -P skip-integration-tests --no-transfer-progress
```

Passes on an empty project. Zero tests is not a failure. ArchUnit rules added in Weekend 2 with no CI changes needed.

### Dev Container

`.devcontainer/devcontainer.json`:
```json
{
  "name": "labs",
  "image": "mcr.microsoft.com/devcontainers/java:25",
  "mounts": [
    "source=/var/run/docker.sock,target=/var/run/docker.sock,type=bind"
  ],
  "customizations": {
    "vscode": {
      "extensions": [
        "vscjava.vscode-java-pack",
        "redhat.vscode-xml",
        "ms-azuretools.vscode-docker"
      ]
    }
  }
}
```

Docker socket mount (not Docker-in-Docker): host socket is Testcontainers' official recommendation for devcontainers. Codespaces provides per-user VM isolation — socket mount is safe here.

---

## Section 2: Hetzner Infra

### Server

- **Hetzner Cloud CPX32** — 4 vCPU, 8 GB RAM, 160 GB SSD, Nuremberg (eu-central) region, ~€16.65/mo
  - Note: CX33 was unavailable at provisioning time; CPX32 is the equivalent shared AMD tier
- Ubuntu 24.04, provisioned via Hetzner Cloud UI (manual — Terraform is Weekend 6/multicloud)
- Upload SSH public key at creation time (`~/.ssh/raphael_github_personal.pub`)
- Create a dedicated limited OS user now — used in Weekend 5 for metrics writes via SSH (`INSERT` on `pr_metrics` only)
- Actual versions installed: Docker Engine 29.5.0, Docker Compose v5.1.3

Post-provision:
```bash
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
apt install -y ufw
ufw default deny incoming && ufw default allow outgoing
ufw allow ssh && ufw allow 80/tcp && ufw allow 443/tcp && ufw allow 443/udp
ufw --force enable
```

### Docker Compose

`compose/docker-compose.yml` — Weekend 1 stub (Postgres only; Kafka + Temporal added Weekend 2):
```yaml
services:
  caddy:
    image: caddy:2
    restart: always
    ports: ["80:80", "443:443", "443:443/udp"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy_data:/data
      - caddy_config:/config

  postgres:
    image: postgres:17
    restart: always
    environment:
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  caddy_data:
  caddy_config:
  postgres_data:
```

`compose/.env.example`:
```
POSTGRES_PASSWORD=changeme
```

Root `.gitignore` (committed in PR 6):
```
# Build output
target/
*.class

# Secrets — never commit
compose/.env
*.env

# OS
.DS_Store
Thumbs.db
```

### Caddyfile

**PR 6 — stub Caddyfile (no ACME, safe to run before DNS propagates):**

```
:80 {
  respond "coming soon" 200
}
```

No TLS config. Caddy serves HTTP on port 80 only. No ACME challenges, no rate limit risk.

**PR 7 — live Caddyfile (replace after `dig` confirms all .de domains resolve to CX33):**

```
raphaellee.de {
  respond "coming soon" 200
}

transflow.raphaellee.de {
  respond "coming soon" 200
}

dashboard.raphaellee.de {
  respond "coming soon" 200
}

raphaellee.eu {
  redir https://raphaellee.de{uri} permanent
}

transflow.raphaellee.eu {
  redir https://transflow.raphaellee.de{uri} permanent
}

dashboard.raphaellee.eu {
  redir https://dashboard.raphaellee.de{uri} permanent
}
```

After `docker compose restart caddy` with the live Caddyfile, Caddy requests TLS certs automatically via Let's Encrypt HTTP-01 challenge. All 6 domains get individual certs. No wildcard cert required.

### DNS (Cloudflare)

Both `raphaellee.de` and `raphaellee.eu` zones in Cloudflare.

**raphaellee.de zone:**

| Type | Name | Value | Proxy |
|------|------|-------|-------|
| A | `@` | CX33 IP | DNS-only (grey cloud) |
| CNAME | `*` | `raphaellee.de` | DNS-only (grey cloud) |

**raphaellee.eu zone:**

| Type | Name | Value | Proxy |
|------|------|-------|-------|
| A | `@` | CX33 IP | DNS-only (grey cloud) |
| A | `transflow` | CX33 IP | DNS-only (grey cloud) |
| A | `dashboard` | CX33 IP | DNS-only (grey cloud) |

No wildcard CNAME on `.eu` — explicit A records only (avoids DNS-01 requirement for wildcard TLS).

**Proxy must be off (grey cloud).** Caddy does TLS directly; Cloudflare proxying breaks ACME HTTP-01 challenges on port 80.

DNS propagation: up to 1 hour. Start this before the Compose session, verify with `dig transflow.raphaellee.de` before running `docker compose up`.

---

## PR Sequence

| PR | Branch name | Content | Depends on |
|----|-------------|---------|------------|
| 1 | `feat/root-readme` | Root README | — |
| 2 | `feat/stub-readmes` | 8 module stubs | PR 1 merged |
| 3 | `feat/spring-boot-bom` | Root + event-driven pom.xml | — |
| 4 | `feat/ci-workflow` | `.github/workflows/ci.yml` | PR 3 merged (CI needs pom.xml) |
| 5 | `feat/devcontainer` | `.devcontainer/devcontainer.json` | — |
| 6 | `feat/compose-caddy` | `compose/` directory + Caddyfile + root `.gitignore` (see below) | — |
| 7 | `feat/dns-verified` | Caddyfile update with live responding routes | Hetzner up + DNS propagated |

PRs 1, 3, 5, 6 are independent — open in parallel. PRs 2 and 4 have single-PR dependencies.

---

## Smoke Verification Checklist (run after each PR merges)

| PR | Verify |
|----|--------|
| 1 | Root README renders correctly on github.com/raphaelplee/labs |
| 2 | All 8 stubs visible; each has architectural decision, trade-off, NOT in scope, reference link |
| 3 | CI green on first push; `mvn validate` passes locally |
| 4 | CI badge appears on repo; green on push to main |
| 5 | devcontainer.json present; Codespaces "open" button visible (optional W1) |
| 6 | `docker compose up` starts; `docker compose ps` shows caddy + postgres healthy |
| 7 | `dig transflow.raphaellee.de` → CX33 IP; `curl https://transflow.raphaellee.de` → 200 + valid cert |
| 7 | `curl -I https://transflow.raphaellee.eu` → 301 redirect to transflow.raphaellee.de |

Pre-Weekend-2 gate: SB4 compat table above — all three dependencies confirmed before any Java code is written.

## Success Criteria

1. `github.com/raphaelplee/labs` — all 8 stubs visible, root README readable in 60 seconds
2. `mvn test -P skip-integration-tests` passes in CI (green badge)
3. `docker compose up` on CX33 starts Caddy + Postgres without manual intervention
4. `https://transflow.raphaellee.de` and `https://dashboard.raphaellee.de` return 200 with valid TLS
5. `https://transflow.raphaellee.eu` redirects to `https://transflow.raphaellee.de`
6. 7 PRs merged — baseline established for 3x dataset
