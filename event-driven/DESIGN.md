# DESIGN.md — Transflow Dashboard

Design decisions for the `event-driven` dashboard (`src/main/resources/static/index.html`).
Captured during `/plan-design-review` on 2026-05-18. Covers the Keycloak OAuth2 auth PR (Weekend 3).

---

## Design Classification

**Type:** APP UI — developer tooling / internal demo dashboard.
Guiding principle: calm surface hierarchy, dense but readable, utility language, minimal chrome.

---

## Color Token System

Introduced as CSS custom properties in the Keycloak auth PR. All hardcoded hex values replaced with these variables.

```css
:root {
  /* Surfaces */
  --bg-canvas:   #0d1117;   /* Page background */
  --bg-surface:  #161b22;   /* Nav, cards */
  --bg-elevated: #21262d;   /* Blurb, button backgrounds */

  /* Borders */
  --border:      #30363d;   /* All borders */

  /* Text */
  --text-primary: #e6edf3;  /* Body, headings */
  --text-muted:   #8b949e;  /* Secondary labels, descriptions */
  --text-link:    #f0f6fc;  /* Logo text */

  /* Accents */
  --accent-blue:   #58a6ff; /* Links, focus, monospace IDs */
  --accent-green:  #3fb950; /* Connected status, COMPLETED state */
  --accent-red:    #f85149; /* Error status, PAYMENT_FAILED, TIMED_OUT */
  --accent-orange: #f0883e; /* Loading/running states */
  --accent-yellow: #e3b341; /* FULFILLMENT_PROCESSING state */
}
```

**Rule:** No new color values may be introduced without adding them here first. Hardcoded hex is blocked.

---

## Typography

- **Font stack:** `-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif` — correct for a developer tool (matches GitHub's dashboard chrome). Not a typography gap — intentional for this category.
- **UI chrome:** 13px — consistent with GitHub's dense admin UI.
- **Monospace (IDs, saga IDs):** inherited from browser default monospace stack.

---

## Authentication (Keycloak OAuth2 — Weekend 3)

### Auth Strategy

**Mode:** `spring-boot-starter-oauth2-client` with `authorization_code` flow (session-based).
NOT a bearer-token Resource Server. The browser session cookie handles auth automatically.
No changes to existing `fetch()` calls in the dashboard JavaScript.

### Entry Point (Unauthenticated)

Spring Security auto-redirects to Keycloak on first visit. No custom login UI in `index.html`. The dashboard only renders for authenticated sessions.

**Keycloak login page:** Add a custom info block with demo credentials (configured via Keycloak admin console or realm theme). Reviewers who land on the login page must see credentials immediately.

### Authenticated Nav Bar (right side)

```
[transflow]  ● Temporal UI   Kafka UI   Swagger UI  |  raphael.lee   Sign out
```

- Username: `preferred_username` claim from JWT, fetched via `GET /api/me` on page load.
- Text color: `var(--text-muted)` (#8b949e).
- "Sign out" link: `var(--accent-blue)`, turns `var(--accent-red)` on hover (destructive signal).
- Font: 13px, same as existing nav links.
- Mobile (< 600px): hide username text, keep "Sign out" link only.

### GET /api/me Endpoint

New Spring MVC controller:
```
GET /api/me
Response: { "username": "raphael.lee" }
Source: principal.getClaim("preferred_username")
```

Fetched once on page load via JavaScript. Inserts username into `#nav-username` span.

### CSRF Protection

`CookieCsrfTokenRepository.withHttpOnlyFalse()` — Spring writes `XSRF-TOKEN` cookie.

JavaScript CSRF helper (add to `index.html`):
```javascript
function getCsrfToken() {
  return document.cookie.split('; ')
    .find(r => r.startsWith('XSRF-TOKEN='))
    ?.split('=')[1];
}
```

All POST `fetch()` calls add `'X-XSRF-TOKEN': getCsrfToken()` header.

### 401 Mid-Session Handler

When any API call returns 401 (token expired mid-session):
- Show an inline dismissible banner at the top of the page.
- Styling: `var(--accent-red)` border, same token as error badges.
- Content: `Session expired — <a href="/oauth2/authorization/keycloak">Sign in again</a>`
- ARIA: `role="alert" aria-live="assertive"` — screen readers announce immediately.
- No `alert()` dialog. The existing alert-based error handling is replaced for 401s.

### Sign Out

`POST /logout` (Spring Security's default logout endpoint) → redirect to `/` → Spring Security
detects unauthenticated → redirect to Keycloak login page.

No custom "signed out" confirmation page.

### Admin Links (Temporal UI, Kafka UI, Swagger UI)

No change to nav links. They open in new tabs as before.
- Swagger UI: covered by Spring Security (Keycloak session).
- Temporal UI / Kafka UI: separate Caddy `basic_auth` (see `compose/Caddyfile`).

### Blurb Update

Add one sentence to the blurb:
> "Secured with **Keycloak** OAuth2 Resource Server — all API endpoints require a valid JWT."

---

## Existing Component Patterns

| Component       | Pattern                                      | Token                         |
|-----------------|----------------------------------------------|-------------------------------|
| Status badge    | Colored pill, 10px bold, 20px border-radius  | Accent colors per state       |
| Step dot        | 10px circle, pulsing animation for RUNNING   | `var(--accent-orange)`        |
| Trigger button  | Full-width, left-aligned, border hover       | `var(--border)` → blue hover  |
| Loading button  | Orange border, spinner emoji visible         | `var(--accent-orange)`        |
| Saga card       | `var(--bg-surface)`, `var(--border)`, 8px radius | —                         |
| Connection dot  | 8px circle in nav, green/red for status      | `var(--accent-green/red)`     |

**Rule:** New UI elements must use an existing pattern or add a new row to this table.

---

## NOT In Scope (This PR)

| Decision | Rationale |
|---|---|
| Keycloak RP-Initiated Logout (end_session endpoint) | Overkill for a demo — `POST /logout` is sufficient |
| Custom Keycloak login page styling (full theme) | Default Keycloak theme + custom info text is enough |
| Mobile responsive redesign of main layout | Pre-existing desktop-first design; auth adds only nav truncation fix |
| DESIGN.md with full design system documentation | Separate TODO (this file covers auth scope only) |

---

## What Already Exists

- `index.html` color system: all values now in CSS custom properties (see Token System above).
- GitHub-dark theme: established and consistent throughout.
- `pollSagas()` error handling: red status dot in nav (pre-existing).
- `setLoading()` helper: reusable for any button state (pre-existing).
- `waitForNewSaga()` polling helper: reusable (pre-existing).
