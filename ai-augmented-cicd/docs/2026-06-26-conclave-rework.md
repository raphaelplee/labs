# ai-augmented-cicd Rework — Conclave as the Development Engine

**Date:** 2026-06-26
**Status:** Draft (envision stage)
**Module:** `ai-augmented-cicd`

---

## Goal

Rework `ai-augmented-cicd` so it tells one coherent story instead of two
disconnected ones. The AI-augmented *development* workflow now lives in the
[`conclave`](https://github.com/raphaelplee/conclave) repo (the `/executor`
protocol). This module keeps the *delivery* half — AI review in CI and the PR
metrics dashboard — and reframes it as the **measurement layer that proves the
conclave workflow's output**.

Before: "AI-assisted code review and a PR metrics dashboard."
After: "conclave drives the development workflow; this module reviews and
measures its output in CI, and the dashboard is the evidence behind the 3x
productivity claim."

---

## Why

The original module and the conclave repo were built separately and overlapped
conceptually — both are "AI-augmented engineering." Keeping them disjoint
duplicates the narrative (DRY) and weakens the portfolio: a reviewer sees a
review-bot in one place and an executor framework in another with no thread
connecting them. Unifying them turns two half-stories into one end-to-end claim
with a written artifact and live data behind it.

---

## Architecture — Two Layers, One Pipeline

```
 ┌─────────────────────────────┐      ┌──────────────────────────────────┐
 │  DEVELOPMENT  (conclave)     │      │  DELIVERY / MEASUREMENT (this)   │
 │                              │      │                                  │
 │  /executor protocol:         │ PR   │  CI AI review                    │
 │  spec → plan → review → code │ ───► │  (Claude in CI / Ollama local)   │
 │  bound by the Codex          │      │  + PR metrics → Grafana dashboard│
 └─────────────────────────────┘      └──────────────────────────────────┘
            the workflow                     the proof it works
```

- **Layer 1 — Development (conclave, referenced, not vendored).** The
  `/executor` protocol is the AI-augmented dev workflow. This module does not
  re-implement or copy it; it references the `conclave` repo as the source of
  truth (DRY).
- **Layer 2 — Delivery & measurement (this module).** AI code review runs in CI
  with a swappable backend; PR metrics (cycle time, review findings,
  acceptance rate) are collected and surfaced on the Grafana dashboard. This is
  the quantitative evidence for the "3x" claim — the workflow that produces the
  PRs is conclave.

---

## What Carries Over (unchanged decisions)

- **Backend swap stays.** Ollama as the local-dev AI backend, Claude API in CI,
  one review function with a swappable backend. Local devs get instant offline
  feedback; CI gets higher-quality analysis.
- **Copilot CLI still out of scope** — no stable public API as of this build.
- **3x methodology doc** (`docs/three-x-methodology.md`) remains the honest
  measurement write-up. It now attributes the development workflow to conclave
  explicitly and keeps the "directional, not laboratory-controlled" caveat.

---

## What Changes

- **README reframed** around the two-layer pipeline above, with conclave named
  as Layer 1 and linked.
- **3x story attribution** — the methodology doc and dashboard copy point at
  conclave as the workflow under measurement, not a generic "Claude Code
  integration."
- **Root README** stack column for the module adds `conclave`.

---

## NOT in Scope

| Decision | Rationale |
|---|---|
| Vendoring or forking conclave into this repo | conclave is the source of truth; reference it (DRY). |
| Building the metrics collector / dashboard now | Still gated on real PR-cycle data; this rework is narrative + structure only. |
| GitHub Copilot CLI backend | No stable public API as of this build. |
| Changing the Claude/Ollama backend-swap design | Carried over unchanged — it already works. |

---

## Open Questions

1. Does the metrics dashboard measure conclave runs specifically (tagging PRs
   produced via `/executor`), or all PRs regardless of origin? The 3x baseline
   in the design docs is "all PRs before vs after" — keep that, but note the
   workflow behind "after" is conclave.
2. Should `three-x-methodology.md` be authored now (narrative) or stay deferred
   until Weekend 5 data exists? Current plan: keep deferred; this spec only
   sets the framing it must follow.

---

## Success Criteria

- The module README presents one pipeline: conclave (dev) → CI review + metrics
  (delivery/proof), with conclave linked.
- No duplication of the executor/Codex content from conclave — only a reference.
- The backend-swap decision and Copilot-CLI exclusion are preserved verbatim in
  intent.
- Root README reflects conclave in the module's stack.
