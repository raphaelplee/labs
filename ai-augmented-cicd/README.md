# ai-augmented-cicd

End-to-end AI-augmented delivery: [`conclave`](https://github.com/raphaelplee/conclave)
drives the development workflow, and this module reviews and measures its output
in CI. The PR metrics dashboard is the evidence behind the 3x productivity claim.

## Two Layers, One Pipeline

```
 conclave  (/executor: spec → plan → review → code)  ──PR──►  CI AI review + PR metrics → Grafana
        the development workflow                               the proof it works
```

- **Development — conclave (referenced, not vendored).** The `/executor`
  protocol, bound by its Codex, is the AI-augmented dev workflow. This module
  references conclave as the source of truth rather than re-implementing it.
- **Delivery & measurement — this module.** AI code review runs in CI; PR
  metrics (cycle time, review findings, acceptance rate) feed a Grafana
  dashboard that quantifies the workflow's throughput and quality.

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
