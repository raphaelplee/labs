# Weekend 3 Design — Keycloak OAuth2 Auth + UI Toasts

**Date:** 2026-05-18
**Status:** Approved
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
  Keycloak: mem_limit: 512m
  Previous total: ~3.3 GB → New total: ~3.8 GB
  Leaves ~4.2 GB for OS + headroom on 8 GB CPX32
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
  command: start
  environment:
    KC_DB: postgres
    KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
    KC_DB_USERNAME: postgres
    KC_DB_PASSWORD: ${POSTGRES_PASSWORD}
    KC_HOSTNAME: auth.raphaellee.de
    KC_HTTP_ENABLED: "false"
    KC_PROXY_HEADERS: xforwarded
    KEYCLOAK_ADMIN: ${KEYCLOAK_ADMIN}
    KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD}
  depends_on:
    postgres:
      condition: service_healthy
  mem_limit: 512m
```

Postgres init script extended to create the `keycloak` database on first boot:

```sql
-- compose/postgres/init.sql (new file, mounted via volume)
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
  reverse_proxy keycloak:8180
}
```

OIDC endpoints (`.well-known/openid-configuration`, `/protocol/openid-connect/*`) are public — required for the OAuth2 flow. Keycloak admin console (`/admin/*`) is protected by Keycloak's own admin credentials; no Caddy `basic_auth` needed.

---

## Keycloak Realm Setup (Manual — between PR 1 and PR 2)

Configured via admin console at `auth.raphaellee.de/admin` after PR 1 deploys.

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

Shown when any API call returns 401 (session expired mid-session):

```
Session expired — Sign in again   ✕
```

- `var(--accent-red)` border
- `role="alert" aria-live="assertive"` — screen readers announce immediately
- Dismissible (✕ button)
- Replaces any existing `alert()` error handling for 401 responses

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
| API error (non-401) | "Request failed — try again" | `var(--accent-red)` |

Toast component: vanilla JS, no framework. New `#toast-container` div appended to `<body>`. Each toast is a `<div>` with `role="status"` for screen reader compatibility. Auto-dismissed via `setTimeout`. Stacking: newest toast appends below existing ones.

### Blurb Update

One sentence added to the existing architecture blurb:

> "Secured with **Keycloak** OAuth2 — all endpoints require a valid session."

---

## Testing Strategy

### Unit Tests (`@Tag("unit")` — fast path, no containers)

- `SecurityConfigTest` (`@WebMvcTest`): unauthenticated `GET /api/sagas` → 302 redirect (not 401)
- `MeControllerTest` (`@WebMvcTest` + `@WithMockUser`): `GET /api/me` → `{ "username": "user" }`

### Integration Tests (`@Tag("integration")`)

Keycloak Testcontainer (`dasniko/testcontainers-keycloak`) added to existing integration test setup.

- Unauthenticated `POST /api/orders` → 302
- Authenticated `POST /api/orders` → 201 (existing test extended to obtain Keycloak token first)
- CSRF: `POST /api/orders` with session but missing `X-XSRF-TOKEN` → 403

Existing integration tests that make POST requests need one change: obtain a Keycloak token from the Testcontainer before the request.

No new CI workflow needed — existing `integration-tests.yml` picks up the new tests automatically.

---

## PR Sequence

| PR | Branch | Content | Depends on |
|---|---|---|---|
| 1 | `feat/keycloak-compose` | Keycloak service + Caddy `auth.raphaellee.de` route + Postgres `keycloak` DB init | — |
| 2 | `feat/spring-security-oauth2` | `SecurityConfig` + `MeController` + `application.yml` + unit + integration tests | PR 1 merged + Keycloak realm configured manually |
| 3 | `feat/auth-ui` | Nav bar (username + sign out) + 401 banner + toasts + CSRF helper + blurb update | PR 1 merged |

PRs 2 and 3 are independent of each other — open in parallel after PR 1 merges.

**Manual step between PR 1 and PR 2:** Configure `transflow` realm, `transflow-core` client, and `demo` user via Keycloak admin console at `auth.raphaellee.de/admin`. Copy generated `KEYCLOAK_CLIENT_SECRET` into `compose/.env`.

---

## Success Criteria

- `transflow.raphaellee.de` unauthenticated → redirects to Keycloak login page
- Login page shows "Demo credentials: demo / demo123"
- After login: dashboard renders with `demo` username in nav bar
- All trigger buttons fire correctly with CSRF token — sagas run end-to-end
- "Sign out" clears session → returns to Keycloak login page
- Session expiry mid-session → 401 banner appears, not a broken UI
- Toast appears on each trigger action (success and error cases)
- `auth.raphaellee.de` resolves and Keycloak admin console is accessible
- `temporal.raphaellee.de` and `kafka.raphaellee.de` remain accessible via existing Caddy `basic_auth`
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
| Keycloak behind Caddy `basic_auth` | Admin console protected by Keycloak's own admin credentials |
| Spring Authorization Server | Keycloak chosen for name recognition in German enterprise/fintech context |
| Temporal UI / Kafka UI behind Keycloak | Existing Caddy `basic_auth` retained — no change |
| Notification toasts beyond trigger events | Session expiry handled by 401 banner; no other toast cases needed |
| Mobile responsive redesign | Auth adds only nav truncation fix (hide username < 600px) |
