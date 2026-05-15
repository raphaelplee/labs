# observability

Grafana dashboards backed by Postgres, tracking CI/CD metrics at PR granularity.

## Architectural Decision

**Grafana + Postgres as the metrics store instead of a time-series database.**

PR-level metrics (build time, test count, review duration) are low-cardinality and low-frequency — one row per PR event. Postgres handles this comfortably with a standard schema and lets the same Postgres instance serve both the application and the metrics store. No separate time-series database to operate.

## Trade-off

Postgres is not a time-series database. It lacks built-in downsampling, compression, and the query model optimised for high-frequency sensor or log data. For PR-level CI metrics this is a non-issue; for application APM at scale it would be the wrong choice.

## NOT in Scope

Full APM or distributed tracing. This module tracks CI/CD pipeline metrics only.

## Reference

[Grafana documentation](https://grafana.com/docs)
