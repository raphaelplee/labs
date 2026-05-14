# Claude Code Configuration

## AI-Augmented Development

This repo uses [gstack](https://github.com/garrynewman/gstack) skills for structured
planning, review, and shipping workflows, and [superpowers](https://github.com/obra/superpowers)
as the skill runtime.

When a user request matches a workflow (planning, review, debugging, shipping), invoke
the relevant skill via the Skill tool rather than handling it ad-hoc.

## Skill Routing

Full skill list and docs: see gstack. Common routing:

| Request type | Skill |
|---|---|
| Strategy / scope | `/plan-ceo-review` |
| Architecture / build plan | `/plan-eng-review` |
| Code review / diff | `/review` |
| Bug / error | `/investigate` |
| Ship / PR | `/ship` |
| QA / browser testing | `/qa` |
| Brainstorming | `/office-hours` |

When in doubt, invoke the skill. Skills contain their own routing logic.
