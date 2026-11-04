# E-Commerce Microservices Platform

A Java 21 / Spring Boot 3.5 microservices platform built in phases. This README describes only
what exists. See [docs/architecture.md](docs/architecture.md) for the target design, including
the Kafka order saga.

## Status

| Phase | Scope | State |
| --- | --- | --- |
| 1 | Architecture, service boundaries, saga design | Done |
| 2 | Maven parent, shared conventions, API Gateway, auth-service | Not started |
| 3 | product-service, inventory-service | Not started |
| 4 | order-service, payment-service, notification-service | Not started |
| 5 | Kafka saga, Redis caching | Not started |
| 6 | Next.js frontend | Not started |
| 7 | Broader tests, OpenAPI aggregation | Not started |
| 8 | Docker and Docker Compose | Not started |
| 9 | Kubernetes and Helm | Not started |
| 10 | Terraform for AWS EKS (plan only until approved) | Not started |
| 11 | GitHub Actions, Jenkins, Argo CD | Not started |
| 12 | Prometheus, Grafana, final docs | Not started |

Phase 1 records the service boundaries, the authentication model, the error contract, and the
order saga. No service is implemented yet.
