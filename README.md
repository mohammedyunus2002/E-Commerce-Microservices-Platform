# E-Commerce Microservices Platform

A Java 21 / Spring Boot 3.5 microservices platform built in phases. This README describes only
what exists and has been verified. See [docs/architecture.md](docs/architecture.md) for the full
target design, including the Kafka order saga.

## Status

| Phase | Scope | State |
| --- | --- | --- |
| 1 | Architecture, service boundaries, saga design | Done |
| 2 | Maven parent, shared conventions, API Gateway, auth-service | Done |
| 3 | product-service with Redis cache, inventory-service | Done |
| 4 | order-service, payment-service, notification-service | Not started |
| 5 | Kafka saga | Not started |
| 6 | Next.js frontend | Not started |
| 7 | Broader tests, OpenAPI aggregation | Not started |
| 8 | Docker and Docker Compose | Not started |
| 9 | Kubernetes and Helm | Not started |
| 10 | Terraform for AWS EKS (plan only until approved) | Not started |
| 11 | GitHub Actions, Jenkins, Argo CD | Not started |
| 12 | Prometheus, Grafana, final docs | Not started |

## Repository layout

```text
ecommerce-platform/
├── backend/
│   ├── pom.xml              # Parent POM: Boot 3.5.16, Cloud 2025.0.3, shared plugins
│   ├── platform-common/     # Servlet-side conventions: errors, correlation IDs, JWT resource server
│   ├── api-gateway/         # Spring Cloud Gateway (WebFlux)
│   ├── auth-service/        # Users, roles, JWT issuance, profiles
│   ├── product-service/     # Catalog, search, Redis cache
│   └── inventory-service/   # Stock levels, reservations
├── docs/architecture.md
├── .env.example             # Placeholder configuration; copy to .env
└── README.md
```

`platform-common` is a small library, not a shared runtime. It holds no entities and no business
logic: the error format, the correlation-ID filter, and JWT resource-server settings. The gateway
does not use it because the gateway runs on the reactive stack.

## What runs today

### API Gateway (`:8080`)

- Routes `/api/**` to services through Spring Cloud LoadBalancer (`lb://service-name`). Instance
  addresses come from environment variables now and from Kubernetes Service DNS later.
- Validates JWT signature, expiry, issuer, and roles before forwarding. Public routes: register,
  login, and GET on products, categories, and stock availability.
- Requires ADMIN for every `/api/admin/**` path.
- Adds or propagates `X-Correlation-Id`, returns it to the client, and logs one access line per request.
- Returns JSON errors: 401, 403, 404 for unknown routes, 503 when a service is down, 504 on timeout.
- CORS for configured origins only (default `http://localhost:3000`).

### Auth Service (`:8081`)

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Public | Create a CUSTOMER account; returns a token |
| POST | `/api/auth/login` | Public | Email and password for a token |
| GET | `/api/users/me` | Authenticated | Own profile |
| PUT | `/api/users/me` | Authenticated | Update name and phone |
| PUT | `/api/users/me/password` | Authenticated | Change password |
| GET | `/api/admin/users?page=&size=` | ADMIN | Paged user list |
| GET | `/api/admin/users/{id}` | ADMIN | One user |
| PUT | `/api/admin/users/{id}/roles` | ADMIN | Replace roles |
| PUT | `/api/admin/users/{id}/status` | ADMIN | Enable or disable login |

- Passwords use BCrypt (cost 12). Password rules: 8–72 characters with at least one letter and one digit.
- Unknown email and wrong password return the same 401 message, and both paths run one BCrypt check.
- Admins cannot remove their own ADMIN role or disable themselves.
- Tokens are HS256 JWTs with `sub` (user ID), `email`, `roles`, `iss`, `exp`, and `jti`.
- Optional first admin from `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`. It is created once and never overwritten.
### Product Service (`:8082`)

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/api/products?q=&categoryId=&minPrice=&maxPrice=&page=&size=&sort=` | Public | Search active products |
| GET | `/api/products/{id}` | Public | One active product (404 if inactive) |
| GET | `/api/categories` | Public | All categories |
| GET | `/api/admin/products?...&activeOnly=` | ADMIN | Search including inactive products |
| GET | `/api/admin/products/{id}` | ADMIN | Any product |
| POST | `/api/admin/products` | ADMIN | Create; `201` with `Location` |
| PUT | `/api/admin/products/{id}` | ADMIN | Update; the SKU cannot change |
| DELETE | `/api/admin/products/{id}` | ADMIN | Deactivate (soft delete; order history keeps working) |
| POST, PUT, DELETE | `/api/admin/categories[/{id}]` | ADMIN | Manage categories; deleting one that has products returns `409 CATEGORY_IN_USE` |

- `q` matches name and description, case-insensitive. `%` and `_` are matched literally.
- `sort` accepts `name`, `price`, `createdAt`, or `newest`, optionally with `,asc` or `,desc`.
  Anything else returns `400 INVALID_SORT`. Page size is capped at 100.
- Prices are `DECIMAL(12,2)` and must be positive (checked in the request and by a database constraint).
- Single products and the category list are cached in Redis. See
  [Product cache](docs/architecture.md#product-cache-redis) for TTLs and invalidation. If Redis
  is down, the service keeps working from MySQL.

### Inventory Service (`:8083`)

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/api/inventory/availability/{productId}` | Public | Units available; unknown products report 0 |
| GET | `/api/inventory/availability?productIds=1,2,3` | Public | Up to 100 products at once |
| GET | `/api/admin/inventory?lowStockThreshold=&page=&size=` | ADMIN | Stock records, lowest first |
| GET | `/api/admin/inventory/{productId}` | ADMIN | Available and reserved units |
| PUT | `/api/admin/inventory/{productId}` | ADMIN | Set available units; the first time, confirms the product exists in product-service |
| POST | `/api/admin/inventory/{productId}/adjust` | ADMIN | `{delta, reason}`; `409 INSUFFICIENT_STOCK` if it would go negative |
| GET | `/api/admin/inventory/reservations?orderId=` | ADMIN | Reservation lines for an order |

- Reserving, releasing, and committing stock for orders is implemented and tested in
  `ReservationService` but has no HTTP endpoint on purpose: only the order saga will call it,
  through Kafka, in Phase 5.
- Stock cannot be oversold under concurrency. See
  [Stock reservations](docs/architecture.md#stock-reservations) for how.

### All services

- Swagger UI at `/swagger-ui.html` and OpenAPI JSON at `/v3/api-docs` on each service port.
  Set `API_DOCS_ENABLED=false` to turn both off.
- Actuator: `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`.

## Prerequisites

- JDK 21 and Maven 3.9+
- Docker, for MySQL and Redis locally and for the Testcontainers tests

## Build and test

```bash
cd ecommerce-platform/backend
mvn clean verify
```

This runs 101 tests across five modules: common 4, gateway 16, auth 24, product 31, inventory 26.

Four test classes use Testcontainers and need Docker. Without Docker they are skipped, not failed.

- `MySqlMigrationTest` (auth) and `ProductMySqlMigrationTest` apply the Flyway migrations to MySQL 8.4.
- `ProductCacheIntegrationTest` runs the cache against Redis 7.4.
- `ReservationConcurrencyMySqlTest` checks under real MySQL row locks that stock is never oversold,
  that orders sharing products don't deadlock, and that duplicate deliveries reserve once.

Everything else uses in-memory H2 in MySQL mode.

Build or test one service and the modules it depends on:

```bash
mvn -pl auth-service -am verify
```

## Run locally

Docker Compose comes in Phase 8. Until then, start MySQL and Redis in containers and run the jars.
Both containers bind to `127.0.0.1` only.

```bash
# 1. Generated credentials for this shell session
export JWT_SECRET=$(openssl rand -base64 48)
export AUTH_DB_PASSWORD=$(openssl rand -hex 16) PRODUCT_DB_PASSWORD=$(openssl rand -hex 16) \
       INVENTORY_DB_PASSWORD=$(openssl rand -hex 16)

# 2. MySQL 8.4 with one database and one user per service
docker run -d --name ecom-mysql -p 127.0.0.1:3306:3306 \
  -e MYSQL_ROOT_PASSWORD="$(openssl rand -hex 16)" mysql:8.4
# Ping over TCP: during first-start initialization MySQL runs a temporary socket-only server.
until docker exec ecom-mysql sh -c 'mysqladmin ping -h127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' \
  2>/dev/null; do sleep 2; done
docker exec -i ecom-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD"' <<SQL
CREATE DATABASE auth_db;      CREATE USER 'auth_user'@'%'      IDENTIFIED BY '$AUTH_DB_PASSWORD';
GRANT ALL ON auth_db.*      TO 'auth_user'@'%';
CREATE DATABASE product_db;   CREATE USER 'product_user'@'%'   IDENTIFIED BY '$PRODUCT_DB_PASSWORD';
GRANT ALL ON product_db.*   TO 'product_user'@'%';
CREATE DATABASE inventory_db; CREATE USER 'inventory_user'@'%' IDENTIFIED BY '$INVENTORY_DB_PASSWORD';
GRANT ALL ON inventory_db.* TO 'inventory_user'@'%';
SQL

# 3. Redis 7.4 for the product cache
docker run -d --name ecom-redis -p 127.0.0.1:6379:6379 redis:7.4-alpine

# 4. Build
cd ecommerce-platform/backend && mvn -q -DskipTests package

# 5. Services (Flyway creates each schema on first start).
# Each process sets its own DB_URL so a leftover DB_URL in the shell cannot point them at the wrong database.
DB_URL='jdbc:mysql://127.0.0.1:3306/auth_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
DB_USERNAME=auth_user DB_PASSWORD="$AUTH_DB_PASSWORD" \
BOOTSTRAP_ADMIN_EMAIL=admin@shop.local BOOTSTRAP_ADMIN_PASSWORD='choose-a-long-password' \
  java -jar auth-service/target/auth-service.jar &
DB_URL='jdbc:mysql://127.0.0.1:3306/product_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
DB_USERNAME=product_user DB_PASSWORD="$PRODUCT_DB_PASSWORD" \
  java -jar product-service/target/product-service.jar &
DB_URL='jdbc:mysql://127.0.0.1:3306/inventory_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
DB_USERNAME=inventory_user DB_PASSWORD="$INVENTORY_DB_PASSWORD" \
  java -jar inventory-service/target/inventory-service.jar &
java -jar api-gateway/target/api-gateway.jar &
```

Try it through the gateway:

```bash
# Wait until every service reports ready
for port in 8081 8082 8083 8080; do
  until curl -sf localhost:$port/actuator/health/readiness >/dev/null; do sleep 2; done
done

curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"Secret123","firstName":"Jane","lastName":"Doe"}'

TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"Secret123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

curl -s localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"

# As the bootstrap admin: create a category, a product, and its stock
ADMIN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@shop.local","password":"choose-a-long-password"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

curl -s -X POST localhost:8080/api/admin/categories -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"name":"Audio"}'
curl -s -X POST localhost:8080/api/admin/products -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"sku":"HEAD-100","name":"Studio Headphones","price":199.90,"categoryId":1}'
curl -s -X PUT localhost:8080/api/admin/inventory/1 -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"availableQuantity":25}'

# Public reads
curl -s 'localhost:8080/api/products?q=studio&sort=price,asc'
curl -s localhost:8080/api/inventory/availability/1
```

### Configuration

| Variable | Used by | Default | Required |
| --- | --- | --- | --- |
| `JWT_SECRET` | all | none | Yes, 32+ bytes |
| `JWT_ISSUER` | all | `ecommerce-auth-service` | No |
| `JWT_ACCESS_TOKEN_TTL` | auth-service | `60m` | No |
| `DB_URL` | each service with a database | `jdbc:mysql://127.0.0.1:3306/<service>_db?...` | No |
| `DB_USERNAME`, `DB_PASSWORD` | each service with a database | none | Yes |
| `DB_POOL_MAX_SIZE` | each service with a database | `10` | No |
| `SERVER_PORT` | all | 8080–8083 | No |
| `BCRYPT_STRENGTH` | auth-service | `12` | No |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | auth-service | blank (skip) | No |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | product-service | `127.0.0.1`, `6379`, blank | No |
| `PRODUCT_CACHE_TTL`, `CATEGORY_CACHE_TTL` | product-service | `10m`, `30m` | No |
| `PRODUCT_SERVICE_URL` | inventory-service | `http://localhost:8082` | No |
| `API_DOCS_ENABLED` | auth, product, inventory | `true` | No; set `false` in production |
| `CORS_ALLOWED_ORIGINS` | gateway | `http://localhost:3000` | No |
| `AUTH_SERVICE_URI` … `NOTIFICATION_SERVICE_URI` | gateway | `http://localhost:8081` … `:8086` | No |

The services refuse to start without `JWT_SECRET` and database credentials. No secrets have defaults.
Every service reads its own `DB_*` variables; `.env.example` prefixes them per service
(`AUTH_DB_*`, `PRODUCT_DB_*`, `INVENTORY_DB_*`) so one file can hold all of them.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Startup fails: `security.jwt.secret must be at least 32 bytes` | Short or missing `JWT_SECRET` | `export JWT_SECRET=$(openssl rand -base64 48)` |
| Gateway returns 401 for a token auth-service just issued | Gateway and auth-service use different `JWT_SECRET` or `JWT_ISSUER` | Export the same values for both |
| Gateway returns 503 `SERVICE_UNAVAILABLE` | Target service is not running, or its `*_SERVICE_URI` is wrong | Start the service or fix the URI |
| `Communications link failure` / `Connection refused` to MySQL while the container is up | `localhost` resolves to IPv6 `::1`, but Docker published the port on IPv4 `127.0.0.1` only | Use `127.0.0.1` in `DB_URL` and `REDIS_HOST` (the defaults already do) |
| Testcontainers tests show as skipped | Docker is not reachable | Start Docker; the H2 tests still run |
| product-service logs `Cache read failed ... using the database` | Redis is down or `REDIS_*` is wrong | Start Redis; requests keep working from MySQL meanwhile |
| `PUT /api/admin/inventory/{id}` returns `503 PRODUCT_SERVICE_UNAVAILABLE` | product-service is down or `PRODUCT_SERVICE_URL` is wrong | Start product-service or fix the URL; existing stock records can still be updated |
| `PUT /api/admin/inventory/{id}` returns 404 | The product ID does not exist in product-service | Create the product first |
| `Access denied for user` at startup | Wrong `DB_USERNAME` or `DB_PASSWORD` | Match the container's `MYSQL_USER` and `MYSQL_PASSWORD` |
