# E-Commerce Microservices Platform

A Java 21 / Spring Boot 3.5 microservices platform built in phases. This README describes only
what exists and has been verified. See [docs/architecture.md](docs/architecture.md) for the
service boundaries and the order saga.

## Status

| Phase | Scope | State |
| --- | --- | --- |
| 1 | Architecture, service boundaries, saga design | Done |
| 2 | Maven parent, shared conventions, API Gateway, auth-service | Done |
| 3 | product-service with Redis cache, inventory-service | Done |
| 4 | order-service, payment-service, notification-service | Done |
| 5 | Kafka order saga | Done |
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
│   ├── platform-messaging/  # Saga envelope, transactional outbox, idempotent consumers
│   ├── api-gateway/         # Spring Cloud Gateway (WebFlux)
│   ├── auth-service/        # Users, roles, JWT issuance, profiles
│   ├── product-service/     # Catalog, search, Redis cache
│   ├── inventory-service/   # Stock levels, reservations
│   ├── order-service/       # Carts and orders; the only writer of order status
│   ├── payment-service/     # Simulated charges and refunds
│   ├── notification-service/ # Stored email records; no mail is sent
│   └── saga-tests/          # Cross-service saga test; not a deployable service
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

- There is no HTTP endpoint for reserving stock. The order saga calls `ReservationService`:
  `order-created` reserves, `order-confirmed` commits, and `order-cancelled` releases reserved
  units and restocks units that were already committed.
- Stock cannot be oversold under concurrency. See
  [Stock reservations](docs/architecture.md#stock-reservations) for how.

### Order Service (`:8084`)

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/api/cart` | Authenticated | The caller's cart |
| POST | `/api/cart/items` | Authenticated | Add quantity to a product; merged lines cannot exceed 100 |
| PUT | `/api/cart/items` | Authenticated | Set a line's quantity |
| DELETE | `/api/cart/items/{productId}` | Authenticated | Remove a line |
| POST | `/api/orders` | Authenticated | Place an order from a request body; `201` with `Location` |
| POST | `/api/orders/checkout` | Authenticated | Place an order from the cart and empty it; `201` |
| GET | `/api/orders?status=&page=&size=` | Authenticated | The caller's orders |
| GET | `/api/orders/{id}` | Authenticated | One of the caller's orders; another customer's order is 404 |
| POST | `/api/orders/{id}/cancel` | Authenticated | Cancel. A confirmed order stays `CONFIRMED` until the refund is recorded |
| GET | `/api/admin/orders`, `/api/admin/orders/{id}` | ADMIN | Every order, or one order |

- The cart is stored in MySQL. Redis key `order-service:carts::{userId}` is a cache (TTL
  `CART_CACHE_TTL`, default 30 minutes). A Redis outage logs a warning and serves the cart from
  MySQL. Set `CART_CACHE_ENABLED=false` to skip the cache. Redis health is excluded from readiness.
- Prices are copied from product-service at order time. The client cannot send a price. Inactive
  products are 404. Duplicate product lines are merged. An order holds at most 50 products, currency
  is USD, and the total is `DECIMAL(12,2)`.
- `POST /api/orders` and checkout return `201` with status `CREATED`. The response does not wait
  for stock or payment. The client polls `GET /api/orders/{id}`.
- With the saga on (the default), placing an order writes an `order-created` event in the same
  database transaction. A relay publishes it. Inventory reserves stock, payment charges the
  snapshotted total, and the order becomes `CONFIRMED`. The same path commits the reservation.
- Cancelling `CREATED`, `INVENTORY_RESERVED`, or `PAYMENT_PENDING` sets the order to `CANCELLED`
  and asks inventory to release stock. Cancelling `CONFIRMED` sets `cancelRequested` and leaves
  the status at `CONFIRMED` until `payment-refunded` moves it to `REFUNDED`. Inventory restocks
  the committed units. `FAILED`, `CANCELLED`, and `REFUNDED` return `409 ORDER_NOT_CANCELLABLE`.
- Set `SAGA_ENABLED=false` to skip Kafka. Orders stay `CREATED`, and cancel works only for that
  status. The HTTP payment call still works in that mode.
- Checkout prices the cart, then re-reads it. If the lines changed, the call returns
  `409 CART_CHANGED` and the cart stays as it is.

### Payment Service (`:8085`)

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| POST | `/api/payments` | Authenticated | Charge one of the caller's `CREATED` orders for its total; `201` the first time |
| GET | `/api/payments` | Authenticated | The caller's payments |
| GET | `/api/payments/{orderId}` | Authenticated | The payment for one of the caller's orders |
| GET | `/api/admin/payments` | ADMIN | Every payment |
| POST | `/api/admin/payments/{orderId}/refund` | ADMIN | Refund a captured payment |

- The charge loads the order from order-service with the caller's token before opening a database
  transaction. The amount is the order total. An order that is not `CREATED` returns
  `409 ORDER_NOT_PAYABLE`.
- Amounts whose cents are 13 (for example `10.13`) are declined, stored as `FAILED` with
  `CARD_DECLINED`, and returned as `201`. Other amounts are stored as `CAPTURED`.
- A second charge of a `CAPTURED` or `REFUNDED` payment returns the existing row with `200` and
  does not charge again. A `FAILED` payment can be retried. Refunding a captured payment twice
  returns the refunded row both times. Refunding a failed payment returns `409 PAYMENT_NOT_REFUNDABLE`.
- The saga charges from the `payment-requested` event. It uses the amount on the event, not a
  second HTTP read. A captured charge publishes `payment-completed`; a decline publishes
  `payment-failed`. `order-cancelled` refunds a captured payment and publishes `payment-refunded`.
- `POST /api/payments` is still there and still requires the order to be `CREATED`. While the saga
  is on, that call races the saga: the first writer stores the payment, and the other gets the
  existing row or `409 ORDER_NOT_PAYABLE` once the order has left `CREATED`. Poll the order instead
  of charging it by hand.
- A payment does not change the order's status. Order-service is the only writer of that status.

### Notification Service (`:8086`)

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/api/notifications` | Authenticated | Email records stored for the caller |
| POST | `/api/admin/notifications` | ADMIN | Store a record; `201`. Nothing is sent |
| GET | `/api/admin/notifications` | ADMIN | Every stored record |

- Types are `ORDER_RECEIVED`, `ORDER_CONFIRMED`, `ORDER_CANCELLED`, `PAYMENT_FAILED`, and
  `PAYMENT_REFUNDED`. There is no SMTP client. The saga stores `ORDER_CONFIRMED`,
  `ORDER_CANCELLED` (except when the reason is `PAYMENT_FAILED`), `PAYMENT_FAILED`, and
  `PAYMENT_REFUNDED`. It consumes `payment-completed` and does not store a second email for it.
  `ORDER_RECEIVED` is only what an admin records by hand.

### All services

- Swagger UI at `/swagger-ui.html` and OpenAPI JSON at `/v3/api-docs` on each service port.
  Set `API_DOCS_ENABLED=false` to turn both off.
- Actuator: `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/prometheus`.

## Prerequisites

- JDK 21 and Maven 3.9+
- Docker, for MySQL, Redis, and Kafka locally and for the Testcontainers tests

## Build and test

```bash
cd ecommerce-platform/backend
mvn clean verify
```

This runs 124 tests: common 4, gateway 17, auth 24, product 31, inventory 27, order 10,
payment 5, notification 1, and saga-tests 5. `platform-messaging` has no tests of its own.

Six test classes use Testcontainers and need Docker. Without Docker they are skipped, not failed.

- `MySqlMigrationTest` (auth), `ProductMySqlMigrationTest`, and `OrderMySqlMigrationTest` apply the
  Flyway migrations to MySQL 8.4. The order test checks that MySQL rejects a non-positive total.
- `ProductCacheIntegrationTest` runs the cache against Redis 7.4.
- `ReservationConcurrencyMySqlTest` checks under real MySQL row locks that stock is never oversold,
  that orders sharing products don't deadlock, and that duplicate deliveries reserve once.
- `SagaFlowTest` starts order, inventory, payment, and notification against one Kafka 3.8.1 broker
  and checks a confirmed order, a declined payment, a confirmed-order refund, an out-of-stock
  rejection, and a dead-letter topic.

Everything else uses in-memory H2 in MySQL mode.

Build or test one service and the modules it depends on:

```bash
mvn -pl auth-service -am verify
```

## Run locally

Docker Compose comes in Phase 8. Until then, start MySQL, Redis, and Kafka in containers and run
the jars. The containers bind to `127.0.0.1` only.

```bash
# 1. Generated credentials for this shell session
export JWT_SECRET=$(openssl rand -base64 48)
export AUTH_DB_PASSWORD=$(openssl rand -hex 16) PRODUCT_DB_PASSWORD=$(openssl rand -hex 16) \
       INVENTORY_DB_PASSWORD=$(openssl rand -hex 16) ORDER_DB_PASSWORD=$(openssl rand -hex 16) \
       PAYMENT_DB_PASSWORD=$(openssl rand -hex 16) NOTIFICATION_DB_PASSWORD=$(openssl rand -hex 16)

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
CREATE DATABASE order_db; CREATE USER 'order_user'@'%' IDENTIFIED BY '$ORDER_DB_PASSWORD';
GRANT ALL ON order_db.* TO 'order_user'@'%';
CREATE DATABASE payment_db; CREATE USER 'payment_user'@'%' IDENTIFIED BY '$PAYMENT_DB_PASSWORD';
GRANT ALL ON payment_db.* TO 'payment_user'@'%';
CREATE DATABASE notification_db; CREATE USER 'notification_user'@'%' IDENTIFIED BY '$NOTIFICATION_DB_PASSWORD';
GRANT ALL ON notification_db.* TO 'notification_user'@'%';
SQL

# 3. Redis 7.4 for the product cache and the cart cache
docker run -d --name ecom-redis -p 127.0.0.1:6379:6379 redis:7.4-alpine

# 4. Kafka 3.8.1 (KRaft, one node). Clients use 127.0.0.1:9092.
# Verified: a host producer and consumer round-tripped a record on this command.
docker run -d --name ecom-kafka -p 127.0.0.1:9092:9092 \
  -e KAFKA_NODE_ID=1 \
  -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://127.0.0.1:9092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@127.0.0.1:9093 \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  -e KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0 \
  apache/kafka-native:3.8.1

# 5. Build
cd ecommerce-platform/backend && mvn -q -DskipTests package

# 6. Services (Flyway creates each schema on first start).
# Run the -exec jar. The file without -exec is the plain jar other modules compile against.
# Each process sets its own DB_URL so a leftover DB_URL in the shell cannot point them at the wrong database.
DB_URL='jdbc:mysql://127.0.0.1:3306/auth_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
DB_USERNAME=auth_user DB_PASSWORD="$AUTH_DB_PASSWORD" \
BOOTSTRAP_ADMIN_EMAIL=admin@shop.local BOOTSTRAP_ADMIN_PASSWORD='choose-a-long-password' \
  java -jar auth-service/target/auth-service-exec.jar &
DB_URL='jdbc:mysql://127.0.0.1:3306/product_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
DB_USERNAME=product_user DB_PASSWORD="$PRODUCT_DB_PASSWORD" \
  java -jar product-service/target/product-service-exec.jar &
DB_URL='jdbc:mysql://127.0.0.1:3306/inventory_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
DB_USERNAME=inventory_user DB_PASSWORD="$INVENTORY_DB_PASSWORD" \
  java -jar inventory-service/target/inventory-service-exec.jar &
DB_URL='jdbc:mysql://127.0.0.1:3306/order_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
DB_USERNAME=order_user DB_PASSWORD="$ORDER_DB_PASSWORD" \
  java -jar order-service/target/order-service-exec.jar &
DB_URL='jdbc:mysql://127.0.0.1:3306/payment_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
DB_USERNAME=payment_user DB_PASSWORD="$PAYMENT_DB_PASSWORD" \
  java -jar payment-service/target/payment-service-exec.jar &
DB_URL='jdbc:mysql://127.0.0.1:3306/notification_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC' \
DB_USERNAME=notification_user DB_PASSWORD="$NOTIFICATION_DB_PASSWORD" \
  java -jar notification-service/target/notification-service-exec.jar &
java -jar api-gateway/target/api-gateway-exec.jar &
```

Try it through the gateway:

```bash
# Wait until every service reports ready
for port in 8081 8082 8083 8084 8085 8086 8080; do
  until curl -sf localhost:$port/actuator/health/readiness >/dev/null; do sleep 2; done
done

curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"Secret123","firstName":"Jane","lastName":"Doe"}'

TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"jane@example.com","password":"Secret123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

curl -s localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"
USER_ID=$(curl -s localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')

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

# Place an order. The saga reserves stock, charges the card, and confirms the order.
# Leave POST /api/payments alone while the saga is on; that call races it.
ORDER=$(curl -s -X POST localhost:8080/api/orders -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"lines":[{"productId":1,"quantity":1}]}')
echo "$ORDER"
ORDER_ID=$(echo "$ORDER" | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
  STATUS=$(curl -s "localhost:8080/api/orders/$ORDER_ID" -H "Authorization: Bearer $TOKEN" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["status"])')
  echo "$STATUS"
  [ "$STATUS" = "CONFIRMED" ] && break
  sleep 1
done
curl -s localhost:8080/api/notifications -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/inventory/availability/1

# Cancel the confirmed order. It stays CONFIRMED until the refund is stored, then becomes REFUNDED.
curl -s -X POST "localhost:8080/api/orders/$ORDER_ID/cancel" -H "Authorization: Bearer $TOKEN"
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
  STATUS=$(curl -s "localhost:8080/api/orders/$ORDER_ID" -H "Authorization: Bearer $TOKEN" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["status"])')
  echo "$STATUS"
  [ "$STATUS" = "REFUNDED" ] && break
  sleep 1
done
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
| `SERVER_PORT` | all | 8080–8086 | No |
| `BCRYPT_STRENGTH` | auth-service | `12` | No |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | auth-service | blank (skip) | No |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | product-service, order-service | `127.0.0.1`, `6379`, blank | No |
| `PRODUCT_CACHE_TTL`, `CATEGORY_CACHE_TTL` | product-service | `10m`, `30m` | No |
| `CART_CACHE_ENABLED`, `CART_CACHE_TTL` | order-service | `true`, `30m` | No |
| `PRODUCT_SERVICE_URL` | inventory-service, order-service | `http://localhost:8082` | No |
| `ORDER_SERVICE_URL` | payment-service | `http://localhost:8084` | No |
| `KAFKA_BOOTSTRAP_SERVERS` | order, inventory, payment, notification | `127.0.0.1:9092` | No |
| `SAGA_ENABLED` | those four services | `true` | No; `false` skips Kafka and leaves orders at `CREATED` |
| `SAGA_OUTBOX_DELAY` | those four services | `1s` | No; how often the outbox relay publishes |
| `SAGA_RETRY_INITIAL`, `SAGA_RETRY_MAX_ELAPSED` | those four services | `500ms`, `30s` | No; then the message goes to `<topic>-dlt` |
| `API_DOCS_ENABLED` | every service except the gateway | `true` | No; set `false` in production |
| `CORS_ALLOWED_ORIGINS` | gateway | `http://localhost:3000` | No |
| `AUTH_SERVICE_URI` … `NOTIFICATION_SERVICE_URI` | gateway | `http://localhost:8081` … `:8086` | No |

The services refuse to start without `JWT_SECRET` and database credentials. No secrets have defaults.
Every service reads its own `DB_*` variables; `.env.example` prefixes them per service
(`AUTH_DB_*`, `PRODUCT_DB_*`, `INVENTORY_DB_*`, `ORDER_DB_*`, `PAYMENT_DB_*`, `NOTIFICATION_DB_*`)
so one file can hold all of them.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Startup fails: `security.jwt.secret must be at least 32 bytes` | Short or missing `JWT_SECRET` | `export JWT_SECRET=$(openssl rand -base64 48)` |
| Gateway returns 401 for a token auth-service just issued | Gateway and auth-service use different `JWT_SECRET` or `JWT_ISSUER` | Export the same values for both |
| Gateway returns 503 `SERVICE_UNAVAILABLE` | Target service is not running, or its `*_SERVICE_URI` is wrong | Start the service or fix the URI |
| `Communications link failure` / `Connection refused` to MySQL while the container is up | `localhost` resolves to IPv6 `::1`, but Docker published the port on IPv4 `127.0.0.1` only | Use `127.0.0.1` in `DB_URL` and `REDIS_HOST` (the defaults already do) |
| Testcontainers tests show as skipped | Docker is not reachable | Start Docker; the H2 tests still run |
| product-service or order-service logs a cache failure and keeps serving | Redis is down or `REDIS_*` is wrong | Start Redis; catalog and cart requests keep working from MySQL |
| `POST /api/orders` returns `503 PRODUCT_SERVICE_UNAVAILABLE` | product-service is down or `PRODUCT_SERVICE_URL` is wrong | Start product-service or fix the URL; the cart and existing orders are unchanged |
| `POST /api/payments` returns `503 ORDER_SERVICE_UNAVAILABLE` | order-service is down or `ORDER_SERVICE_URL` is wrong | Start order-service or fix the URL |
| `PUT /api/admin/inventory/{id}` returns `503 PRODUCT_SERVICE_UNAVAILABLE` | product-service is down or `PRODUCT_SERVICE_URL` is wrong | Start product-service or fix the URL; existing stock records can still be updated |
| `PUT /api/admin/inventory/{id}` returns 404 | The product ID does not exist in product-service | Create the product first |
| `Access denied for user` at startup | Wrong `DB_USERNAME` or `DB_PASSWORD` | Match the container's `MYSQL_USER` and `MYSQL_PASSWORD` |
| An order stays `CREATED` and no payment appears | Kafka is down or `KAFKA_BOOTSTRAP_SERVERS` is wrong. The outbox row is already committed; the relay publishes it when the broker is reachable. Readiness does not include Kafka | Start the broker from the run section and wait one relay interval |
| `POST /api/payments` returns `409 ORDER_NOT_PAYABLE` just after placing an order | The saga already moved the order off `CREATED` | Poll `GET /api/orders/{id}` instead of charging by hand |
