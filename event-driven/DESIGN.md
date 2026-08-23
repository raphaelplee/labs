# DESIGN.md — Transflow Dashboard

Design decisions for the `event-driven` dashboard (`src/main/resources/static/index.html`).
Captured during `/plan-design-review` on 2026-05-18; kept in step with the shipped page.

---

## Design Classification

**Type:** APP UI — developer tooling / internal demo dashboard.
Guiding principle: calm surface hierarchy, dense but readable, utility language, minimal chrome.

---

## Color Token System

Every colour in the page is a CSS custom property. No hardcoded hex remains.

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
  --state-done-green: #2ea043; /* Done step badge background */
}
```

**Rule:** No new color values may be introduced without adding them here first. Hardcoded hex is blocked. Alpha variants of existing tokens (e.g., `#1f6feb11`) count as new values and must be added here as named tokens.

---

## Spacing & Sizing

All values derived from `index.html` CSS. No spacing tokens exist yet — these are literals. Any additions should follow this scale rather than introducing arbitrary values.

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

**Rule:** New UI elements must use an existing pattern or add a new row to this table.

---

## Reusable Helpers

Existing JavaScript in `index.html` that new UI should build on rather than duplicate:

- `pollSagas()` / `renderSagas()` — poll `/api/sagas` and repaint the saga list; on failure
  `pollSagas()` flips the nav status dot red.
- `setBtnLoading(btn, on)` — button loading state; reusable for any button.
- `setPaymentButtonsDisabled(disabled, exceptId)` — mutual exclusion across the payment buttons.
- `setStep(n, state)` — drives a step badge through pending / active / done.
- `setHint(id, text, type)` — writes the helper line under a step group.

---

## Known Design Debt

| Gap | Note |
|---|---|
| Mobile layout | Desktop-first by design. No responsive redesign of the main two-panel layout. |
| Authentication | The demo is unauthenticated. A Keycloak OAuth2 session was designed and not built; the admin UIs behind `temporal.` and `kafka.` rely on Caddy `basic_auth` instead. |
| HTMX rewrite | The page is hand-rolled JS against the REST API. See `TODOS.md`. |
