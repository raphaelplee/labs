# multicloud

Terraform-managed infrastructure spanning AWS and Hetzner, with Docker Compose on EC2 before any Kubernetes.

## Architectural Decision

**EC2-based Docker Compose before EKS.**

The saga demo works on a single server. Adding EKS before the application proves it needs horizontal scaling is premature. Starting with Compose on EC2 keeps infra cost low and lets the Terraform module demonstrate multi-cloud patterns (AWS + Hetzner) without Kubernetes overhead.

## Trade-off

Docker Compose on EC2 is not production-grade for a real subscription platform — no auto-scaling, no zero-downtime deploys. That limitation is explicit and expected for a portfolio demo at this stage.

## NOT in Scope

Managed Kafka (MSK) or managed Postgres (RDS). Both run in containers for this module.

## Reference

[Terraform documentation](https://developer.hashicorp.com/terraform)
