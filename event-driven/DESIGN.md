# DESIGN.md — Transflow Dashboard

Design system of record for the `event-driven` dashboard (`src/main/resources/static/index.html`).
Started during `/plan-design-review` on 2026-05-18, alongside the Weekend 3 Keycloak auth spec.

**Verified against `index.html` on 2026-08-24.** Sections up to and including
*What Already Exists* describe the page as it ships. The Keycloak auth design was
never implemented and is kept, clearly marked, under *Deferred Design* at the end.

---

## Design Classification

**Type:** APP UI — developer tooling / internal demo dashboard.
Guiding principle: calm surface hierarchy, dense but readable, utility language, minimal chrome.

---

## Color Token System

Introduced as CSS custom properties during the step-by-step UI redesign (`54d000f`). All hardcoded hex values replaced with these variables.

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
  --text-dim:     #6e7681;  /* Tertiary text: step dots, saga sub-text, pending badges */
  --text-link:    #f0f6fc;  /* Logo text */

  /* Accents */
  --accent-blue:   #58a6ff; /* Links, focus, monospace IDs */
  --accent-green:  #3fb950; /* Connected status, COMPLETED state */
  --accent-red:    #f85149; /* Error status, PAYMENT_FAILED, TIMED_OUT */
  --accent-orange: #f0883e; /* Loading/running states */
  --accent-yellow: #e3b341; /* FULFILLMENT_PROCESSING state */

  /* Interactive — GitHub UI chrome blue (distinct from --accent-blue link color) */
  --interactive-blue:      #1f6feb;   /* Active step badge bg, saga-card.current border, AWAITING_PAYMENT badge border */
  --interactive-blue-bg:   #1f6feb11; /* order-ctx box background (very transparent) */
  --interactive-blue-edge: #1f6feb44; /* order-ctx box border */

  /* State — completed/done (distinct from --accent-green status indicator) */
  --state-done-green:    #2ea043; /* Done step badge background */
  --state-done-green-bg: #2ea04322; /* COMPLETED badge background */

  /* Badge tints — saga status pills */
  --badge-await-bg:   #1f6feb22; /* AWAITING_PAYMENT background */
  --badge-fulfill-bg: #9a6700aa; /* FULFILLMENT_PROCESSING background */
  --badge-fulfill-bdr:#9a6700;   /* FULFILLMENT_PROCESSING border */
  --badge-error-bg:   #da363322; /* PAYMENT_FAILED / TIMED_OUT background */
  --badge-error-bdr:  #da3633;   /* PAYMENT_FAILED / TIMED_OUT border */
}
```

**Rule:** No new color values may be introduced without adding them here first. Hardcoded hex is blocked. Alpha variants of existing tokens (e.g., `#1f6feb11`) count as new values and must be added here as named tokens.

The one hardcoded value left in `index.html` is `#fff` on the active and done step badges (`.step-num.active`, `.step-num.done`) — white-on-accent label text, not a palette entry.

---

## Spacing & Sizing

All values derived from `index.html` CSS. No spacing tokens exist yet — these are literals. Any future additions should follow this scale rather than introducing arbitrary values.

### Spacing Scale

| Value | Used for |
|---|---|
| 2px | Button label internal margin (`btn-label margin-bottom`) |
| 3px | Order context label gap (`order-ctx-label margin-bottom`) |
| 4px | Spinner left gap; status dot right margin |
| 6px | Step dot row gap; button bottom margin between siblings |
| 7px | Hint top margin |
| 8px | Step header gap; saga time top margin; order context box y-padding |
| 10px | Order context box x-padding; button y-padding; card header bottom margin; step header bottom margin |
| 12px | Saga card bottom margin |
| 14px | Button x-padding; saga card y-padding |
| 16px | Nav link gap; step group spacing; blurb y-padding; saga panel heading bottom margin; saga card x-padding |
| 20px | Trigger panel padding |
| 24px | Nav x-padding; blurb x-padding; saga panel padding |
| 32px | Empty state vertical padding |

### Component Sizes

| Component | Size | Shape |
|---|---|---|
| Status dot (nav) | 8×8px | circle (`border-radius: 50%`) |
| Step dot (saga card) | 10×10px | circle |
| Step badge (trigger panel) | 18×18px | circle |
| Status badge (pill) | auto × 20px+ | `border-radius: 20px` |
| Button | full-width, 10×14px padding | `border-radius: 6px` |
| Order context box | full-width, 8×10px padding | `border-radius: 6px` |
| Saga card | full-width, 14×16px padding | `border-radius: 8px` |
| Trigger panel | 320px fixed | left grid column |

### Layout

```
nav          (~41px tall — 14px padding × 2 + 13px font)
blurb        (~45px tall — 16px padding × 2 + 13px font, line-height 1.6)
layout grid  height: calc(100vh - 105px)
  ├─ trigger-panel   320px wide, 20px padding, scrollable
  └─ saga-panel      fills remainder, 24px padding, scrollable
```

The `105px` offset in `calc(100vh - 105px)` accounts for nav + blurb + their borders. Adjust if nav or blurb height changes.

### Animation

```css
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
/* Applied to: .step-dot.RUNNING — 1.5s ease-in-out infinite */
```

Intent: communicates an in-progress async state without jarring motion. The 50% opacity floor keeps the dot readable. Do not apply to static elements.

---

## Typography

- **Font stack:** `-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif` — correct for a developer tool (matches GitHub's dashboard chrome). Not a typography gap — intentional for this category.
- **UI chrome:** 13px — consistent with GitHub's dense admin UI.
- **Monospace (IDs, saga IDs):** inherited from browser default monospace stack.

---

## Existing Component Patterns

| Component         | Pattern                                              | Token                                      |
|-------------------|------------------------------------------------------|--------------------------------------------|
| Status badge      | Colored pill, 10px bold, 20px border-radius          | Accent colors per state                    |
| Step dot          | 10px circle, pulsing animation for RUNNING           | `var(--accent-orange)`                     |
| Trigger button    | Full-width, left-aligned, border hover               | `var(--border)` → blue hover               |
| Loading button    | Orange border, spinner emoji visible                 | `var(--accent-orange)`                     |
| Saga card         | `var(--bg-surface)`, `var(--border)`, 8px radius     | —                                          |
| Connection dot    | 8px circle in nav, green/red for status              | `var(--accent-green/red)`                  |
| Step badge        | 18px circle, 3 states: pending (grey) / active (blue) / done (green ✓) | `var(--border)` / `var(--interactive-blue)` / `var(--state-done-green)` |
| Order context box | Blue-tinted inset showing active orderId in monospace | `var(--interactive-blue-bg)` border + bg  |
| Payment button (green) | Full-width, green border on hover            | `var(--accent-green)` on hover             |
| Payment button (red)   | Full-width, red border on hover              | `var(--accent-red)` on hover               |
| Payment button (orange) | Full-width, orange border on hover          | `var(--accent-orange)` on hover            |
| Saga card (active) | Blue border highlight for the in-flight saga         | `var(--interactive-blue)` border           |
| Hint text         | 11px helper text below step groups, line-height 1.5  | `var(--text-dim)`                          |
| Empty state       | Centred 13px text, 32px vertical padding, shown when no sagas exist | `var(--text-muted)`          |
| Demo credential hint | Nav-link suffix rendered via `::after { content: attr(data-u) / attr(data-p) }` — 11px, CSS-generated so the credentials are not selectable DOM text and not crawled | `var(--text-dim)` |

**Rule:** New UI elements must use an existing pattern or add a new row to this table.

Feedback is delivered through the per-step `.hint` elements (`setHint()`), not toasts —
the page has no toast or banner component. See *Deferred Design* for the toast/401-banner
design that was specified but never built.

---

## What Already Exists

- `index.html` color system: all values now in CSS custom properties (see Token System above).
- GitHub-dark theme: established and consistent throughout.
- `pollSagas()` — polls `/api/sagas` every 2s; on failure flips the nav status dot red.
- `renderSagas()` — rebuilds the saga list via DOM APIs (no `innerHTML`).
- `setBtnLoading(btn, on)` — reusable loading state for any button.
- `setPaymentButtonsDisabled(disabled, exceptId)` — locks sibling buttons while a call is in flight.
- `setStep(n, state)` — drives the step badges through pending → active → done.
- `setHint(id, text, type)` — writes the per-step feedback line (`ok` / `err` variants).

---

## Deferred Design — Keycloak OAuth2 + Toasts (Weekend 3, not implemented)

Specified on 2026-05-18 ([spec](../docs/superpowers/specs/2026-05-18-weekend-3-design.md)),
never built. Verified on 2026-08-24: no `spring-boot-starter-oauth2-client` in
`event-driven/pom.xml`, no `keycloak` service in `compose/docker-compose.yml`, no
`auth.` host in `compose/Caddyfile`, and no `/api/me`, sign-out, toast or 401-banner
markup in `index.html`. **The live demo is unauthenticated** — anyone can POST to the
saga endpoints; only the Temporal and Kafka UIs are protected, by Caddy `basic_auth`.

The design below is kept as the decision record for if and when auth is picked up.
Nothing in it describes shipped behaviour.

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
> "Secured with **Keycloak** OAuth2 (authorization_code flow) — all endpoints require a valid session."

Note: The auth mode is `spring-boot-starter-oauth2-client` with browser session cookies — NOT an OAuth2 Resource Server (which would accept bearer tokens from external clients). These are different Spring Security patterns.

### Toasts (also unbuilt)

The same spec added toast notifications: 280px fixed bottom-right, 4px left border,
accent colour per event type, dismiss X with a 44×44px touch target and
`aria-label="Dismiss notification"`, `role="status"`; plus a full-width 401 banner
between nav and panels with `role="alert" aria-live="assertive"`. The shipped page
uses the per-step `.hint` line instead.

---

## Known Design Debt

Deferred deliberately; each is a decision, not an oversight.

| Item | Status | Rationale |
|---|---|---|
| Authentication on the demo | Not built | The saga endpoints are public. Accepted for a portfolio demo; the Keycloak design above is the plan of record if that changes. |
| Mobile responsive layout | Not built | Desktop-first by design — the audience opens this on a laptop. The 320px trigger panel and `calc(100vh - 105px)` grid assume a wide viewport. |
| Toast / banner notification system | Not built | Per-step `.hint` text covers every current feedback case with less machinery. |
| Spacing tokens | Not built | The scale above is documented but still expressed as CSS literals. Tokenise only if a second surface needs it (YAGNI). |
| HTMX / SSE rewrite | Not built | The page polls `/api/sagas` every 2s. Server-Sent Events would remove the poll; tracked in [TODOS.md](../TODOS.md). |
