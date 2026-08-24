# ai-augmented-cicd

**Status: stub — nothing in this module is built yet.** The design below is the
plan of record; see *State* for what exists today.

End-to-end AI-augmented delivery: [`conclave`](https://github.com/raphaelplee/conclave)
drives the development workflow, and this module is to review and measure its output
in CI. The PR metrics dashboard is intended as the evidence behind the 3x productivity
claim.

## Two Layers, One Pipeline

```
 conclave  (/executor: spec → plan → review → code)  ──PR──►  CI AI review + PR metrics → Grafana
        the development workflow                               the proof it works
```

- **Development — conclave (referenced, not vendored).** The `/executor`
  protocol, bound by its TAO, is the AI-augmented dev workflow. This module
  references conclave as the source of truth rather than re-implementing it.
- **Delivery & measurement — this module.** AI code review in CI; PR
  metrics (cycle time, review findings, acceptance rate) feeding a Grafana
  dashboard that quantifies the workflow's throughput and quality.

## State

| Piece | Today |
|---|---|
| conclave `/executor` workflow | Live, in its own repo — the PRs in this repo are produced with it |
| AI code review in CI | Not built. `.github/workflows/` holds `ci.yml` (build → publish → deploy), `integration-tests.yml`, and Dependabot — no review job |
| PR metrics collection | Not built. No `pr_metrics` schema, no collector |
| Grafana dashboard | Not built. `dashboard.raphaellee.de` serves a Caddy "coming soon" response |
| 3x measurement write-up | Not written. The methodology reasoning lives in the private notes repo, not here |

## Architectural Decision

**Ollama as local-dev AI backend; Claude API in CI.**

Local developers get instant feedback via Ollama without API costs or latency. CI runs against Claude for higher-quality analysis. The interface is identical — a single review function with a swappable backend.

## Trade-off

Two backends means two code paths to maintain and output quality that differs between local and CI runs. A developer who sees a clean Ollama pass may still see a Claude finding in CI. The cost is worth it: Ollama runs offline, Claude has no stable local equivalent.

## NOT in Scope

GitHub Copilot CLI integration — no stable public API as of this build.

## Reference

- [conclave](https://github.com/raphaelplee/conclave) — the development workflow under measurement
- [Anthropic API documentation](https://docs.anthropic.com)
- [Rework spec](docs/2026-06-26-conclave-rework.md)
