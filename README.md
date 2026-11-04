# E-Commerce Microservices Platform

A Java 21 / Spring Boot 3.5 microservices platform built in phases. This README describes only
what exists and has been verified. See [docs/architecture.md](docs/architecture.md) for the full
target design, including the Kafka order saga.

## Status

| Phase | Scope | State |
| --- | --- | --- |
| 1 | Architecture, service boundaries, saga design | Done |
| 2 | Maven parent, shared conventions, API Gateway, auth-service | Done |
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

## Repository layout

```text
├── backend/
│   ├── pom.xml              # Parent POM: Boot 3.5.16, Cloud 2025.0.3, shared plugins
│   ├── platform-common/     # Servlet-side conventions: errors, correlation IDs, JWT resource server
│   ├── api-gateway/         # Spring Cloud Gateway (WebFlux)
│   └── auth-service/        # Users, roles, JWT issuance, profiles
├── docs/architecture.md
├── .env.example             # Placeholder configuration; copy to .env
└── README.md
```

`platform-common` is a small library, not a shared runtime. It holds no entities and no business
logic. The gateway does not use it because the gateway runs on the reactive stack.

## What runs today

### API Gateway (`:8080`)

- Routes `/api/**` through Spring Cloud LoadBalancer (`lb://service-name`).
- Public routes: register, login, and GET on products and categories.
- Requires ADMIN for every `/api/admin/**` path.
- Propagates `X-Correlation-Id` and returns JSON errors (401, 403, 404, 503, 504).

### Auth Service (`:8081`)

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Public | Create a CUSTOMER account; returns a token |
| POST | `/api/auth/login` | Public | Email and password for a token |
| GET | `/api/users/me` | Authenticated | Own profile |
| PUT | `/api/users/me` | Authenticated | Update name and phone |
| PUT | `/api/users/me/password` | Authenticated | Change password |
| GET | `/api/admin/users` | ADMIN | Paged user list |
| PUT | `/api/admin/users/{id}/roles` | ADMIN | Replace roles |
| PUT | `/api/admin/users/{id}/status` | ADMIN | Enable or disable login |

Passwords use BCrypt. Tokens are HS256 JWTs. No secrets have defaults.

## Build and test

```bash
cd backend
mvn clean verify
```

This runs 43 tests. The MySQL migration test uses Testcontainers and is skipped when Docker is unavailable.

## Configuration

| Variable | Used by | Required |
| --- | --- | --- |
| `JWT_SECRET` | gateway, auth-service | Yes, 32+ bytes |
| `DB_USERNAME`, `DB_PASSWORD` | auth-service | Yes |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | auth-service | No |
| `CORS_ALLOWED_ORIGINS` | gateway | No |
| `AUTH_SERVICE_URI` | gateway | No |
