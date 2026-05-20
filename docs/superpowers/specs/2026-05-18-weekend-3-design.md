# Weekend 3 Design — Keycloak OAuth2 Auth + UI Toasts

**Date:** 2026-05-18
**Revised:** 2026-05-19 (post eng review), 2026-05-20 (Weekend 2 learnings applied)
**Status:** Approved

> **Amendments from Weekend 2 execution (2026-05-20):**
> 1. **Keycloak JVM heap cap added** — `JAVA_OPTS_APPEND: "-Xms128m -Xmx600m"` required. `mem_limit` is a cgroup hard limit but JVM ergonomics size the heap from host RAM by default. kafka-ui hit this exact bug at `mem_limit: 256m` with no `-Xmx` and OOM-restarted on first page visit.
> 2. **Healthcheck port corrected** — Keycloak 26 serves `/health/ready` on management port **9000**, not main port 8080. Original spec had `localhost:8080/health/ready` which would always fail.
> 3. **Duplicate `command` field removed** — spec had both `command: start-dev` and `command: start-dev --import-realm`; kept the latter (second key wins in YAML but the inconsistency was confusing).
> 4. **`kafka-init` dependency preserved** — original spec's `transflow-core.depends_on` block would have dropped the `kafka-init: service_completed_successfully` condition that ensures Kafka topics exist before the app starts.
**Module:** `event-driven` (`transflow-core`)
**Live demo:** `transflow.raphaellee.de`

---

## Goal

Protect the transflow demo from public POST flooding while adding demonstrable auth skills to the portfolio. All of `transflow.raphaellee.de` moves behind a Keycloak session. A hiring manager who clicks the link sees a login page with demo credentials, then the full dashboard.

Success means: unauthenticated requests to any endpoint are redirected to Keycloak. Authenticated users see the existing dashboard with a username + sign out in the nav bar and toast notifications on trigger events.

---

## Architecture

One new Docker service (Keycloak). Postgres is shared — a `keycloak` database is added to the existing container. One new subdomain exposed via Caddy.

```
Compose stack additions:
  keycloak   quay.io/keycloak/keycloak:26   :8180   auth.raphaellee.de

Shared Postgres:
  keycloak database added to existing postgres container (no new container)

Memory budget:
  Keycloak: mem_limit: 768m   (JVM + Quarkus baseline ~350-400 MB idle; first-login JIT spikes)
  Previous total: ~3.3 GB → New total: ~4.0 GB
  Leaves ~4.0 GB for OS + headroom on 8 GB CPX32
```

### Protection Model

| Subdomain | Protection |
|---|---|
| `transflow.raphaellee.de` | Keycloak session (this PR) |
| `auth.raphaellee.de` | Public OIDC endpoints; admin via Keycloak admin credentials |
| `temporal.raphaellee.de` | Caddy `basic_auth` (existing — no change) |
| `kafka.raphaellee.de` | Caddy `basic_auth` (existing — no change) |

Spring Security protects only `transflow-core`. Temporal UI and Kafka UI retain their existing Caddy `basic_auth` — separate credential set, no change required.

### Auth Flow

```
[Browser] → transflow.raphaellee.de (unauthenticated)
  → Spring Security detects no session
    → redirect to auth.raphaellee.de/realms/transflow/protocol/openid-connect/auth
      → user enters demo / demo123 on Keycloak login page
        → Keycloak issues authorization code → redirect back to transflow.raphaellee.de
          → Spring Security exchanges code for tokens → session established
            → dashboard renders
```

---

## Docker Compose Changes

New `keycloak` service added to `compose/docker-compose.yml`:

```yaml
keycloak:
  image: quay.io/keycloak/keycloak:26
  restart: always
  command: start-dev --import-realm
  environment:
    KC_DB: postgres
    KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
    KC_DB_USERNAME: postgres
    KC_DB_PASSWORD: ${POSTGRES_PASSWORD}
    KC_HOSTNAME: auth.raphaellee.de
    KC_HTTP_ENABLED: "true"       # required — Caddy reverse proxies over HTTP internally
    KC_PROXY_HEADERS: xforwarded  # Keycloak reports HTTPS externally via X-Forwarded headers
    KEYCLOAK_ADMIN: ${KEYCLOAK_ADMIN}
    KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
    JAVA_OPTS_APPEND: "-Xms128m -Xmx600m"  # cap JVM heap — mem_limit is a cgroup hard limit but
                                             # JVM ergonomics size from host RAM by default
  volumes:
    - ./keycloak/realm-export.json:/opt/keycloak/data/import/realm-export.json
  depends_on:
    postgres:
      condition: service_healthy
  healthcheck:
    # Keycloak 26 serves /health/ready on the management port (9000), not the main port (8080)
    test: ["CMD-SHELL", "curl -sf http://localhost:9000/health/ready || exit 1"]
    interval: 15s
    timeout: 10s
    retries: 10
    start_period: 60s
  mem_limit: 768m
```

`transflow-core`'s `depends_on` extended (preserve existing `kafka-init` dependency):

```yaml
transflow-core:
  depends_on:
    temporal:
      condition: service_healthy
    kafka-init:
      condition: service_completed_successfully  # preserve — ensures topics exist before app starts
    keycloak:
      condition: service_healthy   # wait for Keycloak OIDC discovery document to be ready
```

**Postgres volume mount** extended in the `postgres` service:

```yaml
postgres:
  volumes:
    - postgres_data:/var/lib/postgresql/data
    - ./postgres/init.sql:/docker-entrypoint-initdb.d/init.sql  # creates keycloak DB on first boot
```

`compose/postgres/init.sql` (new file):

```sql
CREATE DATABASE keycloak;
```

New env vars in `compose/.env.example`:

```
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=changeme
KEYCLOAK_CLIENT_SECRET=changeme
```

---

## Caddy Changes

New route added to `compose/Caddyfile`:

```
auth.raphaellee.de {
  reverse_proxy keycloak:8080
}
```

Note: Keycloak `start-dev` listens on port 8080 (not 8180 which is the HTTPS port). OIDC endpoints (`.well-known/openid-configuration`, `/protocol/openid-connect/*`) are public — required for the OAuth2 flow. Keycloak admin console (`/admin/*`) is protected by Keycloak's own admin credentials; no Caddy `basic_auth` needed.

---

## Keycloak Realm Setup

### Initial Setup (Manual — after PR 1 deploys)

Configure via admin console at `auth.raphaellee.de/admin` after PR 1 deploys and Keycloak is healthy.

```
Realm:         transflow
Client ID:     transflow-core
Client type:   OpenID Connect — confidential — authorization_code flow
Valid redirect URIs:       https://transflow.raphaellee.de/*
Post-logout redirect URI:  https://transflow.raphaellee.de/

Demo user:
  Username: demo
  Password: demo123

Login page info block:
  "Demo credentials: demo / demo123"
  (configured as realm login theme info text)
```

### Realm Export (required — makes setup reproducible)

After completing the manual setup:

1. Keycloak admin → `transflow` realm → Realm settings → Export → export with clients
2. Save as `compose/keycloak/realm-export.json`
3. Commit the export file

On subsequent `docker compose up` (or server rebuild), Keycloak imports the realm automatically via `--import-realm`. The `KEYCLOAK_CLIENT_SECRET` stays in `compose/.env` — not in the export.

Copy the generated `KEYCLOAK_CLIENT_SECRET` into `compose/.env` before starting PR 2.

---

## Spring Security Config

**Dependency added to `event-driven/pom.xml`:**

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

**`SecurityConfig.java`** (new, in `orchestration` module — owns the web layer):

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
      .authorizeHttpRequests(auth -> auth
        .anyRequest().authenticated()
      )
      .oauth2Login(Customizer.withDefaults())
      .logout(logout -> logout
        .logoutSuccessUrl("/")
      )
      .csrf(csrf -> csrf
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
      );
    return http.build();
  }
}
```

**`application.yml` additions:**

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: transflow-core
            client-secret: ${KEYCLOAK_CLIENT_SECRET}
            scope: openid,profile,email
        provider:
          keycloak:
            issuer-uri: https://auth.raphaellee.de/realms/transflow
```

**`MeController.java`** (new, in `orchestration` module):

```
GET /api/me
→ { "username": "demo" }
Source: OidcUser.getClaim("preferred_username")
```

All existing endpoints — `/api/orders`, `/api/payments`, `/api/sagas`, `/api/fulfillments`, Swagger UI — protected automatically by `anyRequest().authenticated()`. No per-endpoint annotations needed.

---

## UI Changes (`index.html`)

### Nav Bar

Right side of existing nav bar extended:

```
[transflow]  ● Temporal UI   Kafka UI   Swagger UI  |  demo   Sign out
```

- Username fetched via `GET /api/me` on page load → inserted into `#nav-username` span
- "Sign out" → `POST /logout` → Spring Security clears session → redirect to Keycloak login page
- Username: `var(--text-muted)` (#8b949e), 13px
- "Sign out": `var(--accent-blue)` → `var(--accent-red)` on hover (destructive signal)
- Mobile (< 600px): hide `#nav-username`, keep "Sign out" link only

### 401 Mid-Session Banner

Shown when any API call returns 401 (session expired mid-session — distinct from first visit which redirects via oauth2Login):

```
Session expired — Sign in again   ✕
```

- `var(--accent-red)` border
- `role="alert" aria-live="assertive"` — screen readers announce immediately
- Dismissible (✕ button)
- Replaces any existing `alert()` error handling for 401 responses

### 403 Handling

A 403 response means the XSRF-TOKEN cookie is stale (e.g., page sat open, cache cleared). Retrying without a refresh fails identically. On any 403 response: call `window.location.reload()`. This refreshes the CSRF token and re-enables the buttons with no user-visible error message.

### CSRF Helper

Added to `index.html` JavaScript:

```javascript
function getCsrfToken() {
  return document.cookie.split('; ')
    .find(r => r.startsWith('XSRF-TOKEN='))
    ?.split('=')[1];
}
```

All existing `POST fetch()` calls extended with `'X-XSRF-TOKEN': getCsrfToken()` header.

### Toasts

Position: bottom-right, stacked, max 3 visible, auto-dismiss after 4 seconds.

| Event | Message | Color |
|---|---|---|
| Saga trigger success | "Saga started" | `var(--accent-green)` |
| Payment confirmed | "Payment confirmed" | `var(--accent-green)` |
| Payment failed (intentional) | "Payment failed — compensation triggered" | `var(--accent-orange)` |
| API error (non-401, non-403) | "Request failed — try again" | `var(--accent-red)` |

Toast component: vanilla JS, no framework. New `#toast-container` div appended to `<body>`. Each toast is a `<div>` with `role="status"` for screen reader compatibility. Auto-dismissed via `setTimeout`. Stacking: newest toast appends below existing ones.

### Blurb Update

One sentence added to the existing architecture blurb:

> "Secured with **Keycloak** OAuth2 — all endpoints require a valid session."

---

## Testing Strategy

### Unit Tests (`@Tag("unit")` — fast path, no containers)

- `SecurityConfigTest` (`@WebMvcTest`): unauthenticated `GET /api/sagas` → 302 redirect (not 401)
- `MeControllerTest` (`@WebMvcTest` + `@WithOidcLogin(claims = @OidcClaims(preferredUsername = "demo"))`): `GET /api/me` → `{ "username": "demo" }`

Note: `@WithMockUser` injects a `UsernamePasswordAuthenticationToken`, not an `OidcUser`. `@WithOidcLogin` is required for any controller that reads OIDC claims.

### Integration Tests (`@Tag("integration")`)

Keycloak Testcontainer (`dasniko/testcontainers-keycloak`) added to existing integration test setup.

`@DynamicPropertySource` overrides the issuer URI in integration tests:

```java
@DynamicPropertySource
static void keycloakProperties(DynamicPropertyRegistry registry) {
  registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri",
    () -> keycloakContainer.getAuthServerUrl() + "/realms/transflow");
}
```

Tests:
- Unauthenticated `POST /api/orders` → 302
- Authenticated `POST /api/orders` → 201 (existing test extended to obtain Keycloak token first)
- CSRF: `POST /api/orders` with session but missing `X-XSRF-TOKEN` → 403

No new CI workflow needed — existing `integration-tests.yml` picks up the new tests automatically.

---

## PR Sequence

| PR | Branch | Content | Depends on |
|---|---|---|---|
| 1 | `feat/keycloak-compose` | Keycloak service + healthcheck + Caddy `auth.raphaellee.de` route + Postgres init.sql + volume mount | — |
| 2 | `feat/spring-security-oauth2` | `SecurityConfig` + `MeController` + `application.yml` + unit + integration tests | PR 1 merged + Keycloak realm configured + realm-export.json committed |
| 3 | `feat/auth-ui` | Nav bar + 401 banner + 403 reload + toasts + CSRF helper + blurb update | PR 2 merged |

PR 3 code can be written in parallel with PR 2 but **must merge after PR 2** — CSRF cookies are not issued and `GET /api/me` returns 401 until Spring Security is active.

**Steps between PR 1 and PR 2:**
1. Deploy PR 1 to server
2. Configure realm manually via `auth.raphaellee.de/admin`
3. Export realm → commit as `compose/keycloak/realm-export.json`
4. Copy `KEYCLOAK_CLIENT_SECRET` to `compose/.env`
5. Open PR 2

---

## Success Criteria

- `transflow.raphaellee.de` unauthenticated → redirects to Keycloak login page
- Login page shows "Demo credentials: demo / demo123"
- After login: dashboard renders with `demo` username in nav bar
- All trigger buttons fire correctly with CSRF token — sagas run end-to-end
- Stale CSRF token → page reloads silently, buttons re-enable, next trigger succeeds
- "Sign out" clears session → returns to Keycloak login page
- Session expiry mid-session → 401 banner appears, not a broken UI
- Toast appears on each trigger action (success and intentional failure cases)
- `auth.raphaellee.de` resolves and Keycloak admin console is accessible
- `temporal.raphaellee.de` and `kafka.raphaellee.de` remain accessible via existing Caddy `basic_auth`
- `docker compose down -v && docker compose up` → Keycloak imports realm automatically, no manual steps
- Unit tests green in fast CI
- Integration tests green in `integration-tests` CI job

---

## Extensibility — Social Login (Google, Apple)

Keycloak acts as an identity broker. Adding Google Sign-In or Apple Sign-In requires zero Spring Boot code changes — configuration is entirely in the Keycloak admin console.

**To add Google Sign-In:**
1. Create OAuth2 credentials in Google Cloud Console (`client_id` + `client_secret`)
2. In Keycloak admin → `transflow` realm → Identity Providers → Add → Google
3. Enter the Google credentials. Keycloak handles the redirect flow.
4. Done. Spring Boot continues talking to Keycloak via the same `authorization_code` flow — it never sees Google directly.

**To add Apple Sign-In:**
Same pattern, but Apple uses a JWT-based client secret (generated from a `.p8` key file) instead of a plain `client_secret`. Keycloak has a built-in Apple Identity Provider that handles this. Slightly more setup, no code changes.

**Why this matters:** The broker pattern means the identity source is decoupled from the application. Switching from demo credentials to corporate SSO (SAML, LDAP, Google Workspace) is a Keycloak configuration change — not a deployment. This is the correct architecture for any multi-tenant or enterprise-ready system.

---

## NOT in Scope

| Decision | Rationale |
|---|---|
| Keycloak RP-Initiated Logout | `POST /logout` is sufficient for a demo |
| Custom Keycloak login page theme | Default theme + info text block is enough |
| Keycloak `start` (production mode) | Requires pre-built image; `start-dev` is correct for a single-realm demo |
| Spring Authorization Server | Keycloak chosen for name recognition in German enterprise/fintech context |
| Temporal UI / Kafka UI behind Keycloak | Existing Caddy `basic_auth` retained — no change |
| Notification toasts beyond trigger events | Session expiry handled by 401 banner; 403 handled by page reload |
| Mobile responsive redesign | Auth adds only nav truncation fix (hide username < 600px) |

---

## GSTACK REVIEW REPORT

| Review | Trigger | Why | Runs | Status | Findings |
|--------|---------|-----|------|--------|----------|
| CEO Review | `/plan-ceo-review` | Scope & strategy | 0 | — | — |
| Codex Review | `/codex review` | Independent 2nd opinion | 0 | — | — |
| Eng Review | `/plan-eng-review` | Architecture & tests (required) | 1 | CLEAR | 6 issues found, 0 critical gaps |
| Design Review | `/plan-design-review` | UI/UX gaps | 1 | CLEAR | Captured in event-driven/DESIGN.md |
| DX Review | `/plan-devex-review` | Developer experience gaps | 0 | — | — |

- **UNRESOLVED:** 0
- **VERDICT:** ENG CLEARED — ready to implement
