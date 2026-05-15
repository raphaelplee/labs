# ai-augmented-cicd

AI-assisted code review and PR metrics dashboard, using Ollama for local development and Claude in CI.

## Architectural Decision

**Ollama as local-dev AI backend; Claude API in CI.**

Local developers get instant feedback via Ollama without API costs or latency. CI runs against Claude for higher-quality analysis. The interface is identical — a single review function with a swappable backend.

## Trade-off

Two backends means two code paths to maintain and output quality that differs between local and CI runs. A developer who sees a clean Ollama pass may still see a Claude finding in CI. The cost is worth it: Ollama runs offline, Claude has no stable local equivalent.

## NOT in Scope

GitHub Copilot CLI integration — no stable public API as of this build.

## Reference

[Anthropic API documentation](https://docs.anthropic.com)
