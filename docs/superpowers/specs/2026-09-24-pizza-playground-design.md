# Pizza Playground — Design Spec

**Date:** 2026-09-24
**Status:** Approved in brainstorming, awaiting implementation plan
**Repo:** separate repository `pizza-playground` (the KB links to it and pins a version tag)

## 1. Purpose

A self-contained pizza ordering application that anyone can run locally with `docker compose up` and use as the target for the Testing Knowledge Base tracks: JMeter, k6 and Playwright. It replaces the Firebase and Billplz dependencies of the original PizzaDiablo project with a real backend, a real database and a mocked payment gateway, so no cloud accounts are needed.

**Success looks like**
- `docker compose up` on a clean machine gives a working shop at `http://localhost:3000`, an API with Swagger UI at `http://localhost:8080/swagger-ui.html`, and a mock gateway.
- Every test run can start from an identical state with one API call.
- A load test can push order volume without the mock gateway being the bottleneck.
- A UI test can click through menu, cart, checkout, payment and confirmation.

**Non-goals for version one:** preorders, drinks, inventory limits, cashier manual orders, real email, image uploads, refunds, rate limiting, HTTPS.

**Intellectual property:** the original repository belongs to a client. The playground is a rewrite with a different name, no client copy, images or data, and no reference to the client in code or docs.

## 2. Architecture

Four containers defined in `docker-compose.yml`:

| Service | Image | Port | Role |
|---|---|---|---|
| `web` | Next.js 15, built from `web/` | 3000 | Customer shop and admin UI, copied from the source project with all Firebase calls replaced by fetches to `api` |
| `api` | Spring Boot 3, Java 21, built from `api/` | 8080 | All business logic, auth, persistence, gateway client |
| `db` | `postgres:16` | 5432 | Single database, Flyway-managed schema and seed |
| `mock-gateway` | SoapUI open source, built from `mock-gateway/` | 8090 | Mock payment gateway: bill creation, hosted pay page, signed callback |

Single Spring Boot service, package-per-feature (`auth`, `catalog`, `orders`, `payments`, `admin`). No microservices, no message broker.

## 3. Data model

Postgres, migrations in `api/src/main/resources/db/migration/` via Flyway.

| Table | Columns |
|---|---|
| `users` | id, email (unique), password_hash, display_name, role (`CUSTOMER`, `OWNER`, `CASHIER`), created_at |
| `pizzas` | id, name, description, base_price, category, image_url, active |
| `pizza_sizes` | id, name, price_delta |
| `crusts` | id, name, price_delta |
| `toppings` | id, name, price_delta |
| `orders` | id, order_number (sequence from 1001), user_id (nullable), status, payment_status, type (`PICKUP`, `DELIVERY`), customer_name, customer_email, customer_phone, subtotal, service_fee, total, created_at, paid_at |
| `order_items` | id, order_id, pizza_name, size, crust, toppings (JSONB array of names), quantity, line_price |
| `payments` | id, order_id, gateway_bill_id, amount, status, raw_callback (JSONB), created_at |

Rules:
- Order items are snapshots. They never reference `pizzas` by foreign key, so menu edits do not rewrite history.
- There is no `pending_orders` table. An order is `status = PENDING, payment_status = UNPAID` until the callback flips it.
- Order statuses: `PENDING`, `NEW`, `PREPARING`, `READY`, `COMPLETED`, `CANCELLED`. Payment statuses: `UNPAID`, `PAID`, `FAILED`.
- Prices are computed server-side from the lookup tables. The client sends ids and quantities only.

## 4. API surface

Base path `/api/v1`, JSON everywhere.

| Area | Endpoint | Auth |
|---|---|---|
| Auth | `POST /auth/register` | none |
| Auth | `POST /auth/login` → `{ token, user }` | none |
| Auth | `GET /auth/me` | any token |
| Catalog | `GET /menu` → pizzas with sizes, crusts, toppings in one payload | none |
| Orders | `POST /orders` → prices cart, creates order, calls gateway, returns `{ orderId, orderNumber, payUrl }` | optional token |
| Orders | `GET /orders/{id}` | none (id is a UUID) |
| Orders | `GET /orders/mine` | CUSTOMER |
| Payments | `POST /payments/callback` (gateway webhook) | HMAC signature |
| Payments | `GET /payments/{orderId}/status` | none |
| Admin | `GET /admin/orders?status=` | OWNER, CASHIER |
| Admin | `PATCH /admin/orders/{id}/status` | OWNER, CASHIER |
| Admin | `GET /admin/orders/history?from=&to=` | OWNER |
| Playground | `POST /admin/reset[?full=true]` | OWNER |
| Ops | `GET /actuator/health` | none |

Swagger UI is served at `/swagger-ui.html` from springdoc-openapi.

`POST /orders` request body:

```json
{
  "type": "PICKUP",
  "customer": { "name": "…", "email": "…", "phone": "…" },
  "items": [
    { "pizzaId": 1, "sizeId": 2, "crustId": 1, "toppingIds": [3, 5], "quantity": 2 }
  ],
  "autoPay": false
}
```

`autoPay: true` skips the gateway and marks the order `PAID` / `NEW` inside the same transaction. It exists so JMeter and k6 can drive order volume without the mock gateway in the loop.

## 5. Payment flow

1. `POST /orders` validates and prices the cart, inserts the order as `PENDING` / `UNPAID`, then calls `POST {MOCK_GATEWAY_URL}/bills` with `{ amount, orderId, callbackUrl, redirectUrl }`.
2. The mock returns `{ id, url }`. The API stores the bill id in `payments` and returns `url` to the browser as `payUrl`.
3. `payUrl` is the mock's own `GET /pay/{billId}` page: a minimal HTML form with two buttons, **Pay** and **Fail**. This gives Playwright something to click and JMeter a plain POST.
4. **Pay** makes the mock `POST` the callback to `http://api:8080/api/v1/payments/callback` with Billplz-shaped fields (`id`, `paid`, `paid_at`, `amount`, `x_signature`) where `x_signature` is HMAC-SHA256 over the sorted fields using `GATEWAY_SIGNATURE_KEY` from `.env`. The mock then redirects the browser to `redirectUrl`.
5. The callback handler verifies the signature, sets `payment_status = PAID`, `status = NEW`, `paid_at = now()`, stores the raw callback, and logs a `would send email to …` line. A callback for an order that is already `PAID` returns 200 and changes nothing, so retries are safe. **Fail** sends `paid=false` and the order becomes `FAILED` while staying `PENDING`.
6. The confirmation page polls `GET /payments/{orderId}/status` every second until `PAID` or `FAILED`.

Mock extras:
- The mock honours an `X-Mock-Delay: <ms>` request header on `/bills` and `/pay/{billId}` to simulate a slow gateway.
- The mock is a REST MockService defined in `mock-gateway/gateway-mock-soapui-project.xml`. Groovy scripts compute the signature and fire the callback. The same file opens in desktop SoapUI so anyone can edit responses.
- `mock-gateway/Dockerfile` downloads SoapUI open source and runs `mockservicerunner.sh` against the project file. It is the one custom image in the stack.

## 6. Auth

- Passwords hashed with BCrypt. JWT signed with HS256 using `JWT_SECRET` from `.env`, twelve-hour expiry, no refresh tokens.
- Roles: `CUSTOMER` for shop endpoints, `OWNER` and `CASHIER` for admin endpoints, `OWNER` only for reset and order history.
- Guest checkout stays allowed: `POST /orders` without a token creates an order with `user_id = null`.
- Seed accounts, all with password `Password123!`:
  - `owner@playground.local` (OWNER)
  - `cashier@playground.local` (CASHIER)
  - `customer01@playground.local` … `customer20@playground.local` (CUSTOMER)
- `test-data/customers.csv` holds the twenty customer logins for JMeter and k6.

## 7. Reset and seeding

- Flyway `V1__schema.sql` creates the tables and sequence. `V2__seed.sql` inserts about twelve pizzas, three sizes, three crusts, eight toppings, and the seed accounts.
- `POST /admin/reset` truncates `orders`, `order_items`, `payments` and restarts the order number sequence at 1001. With `?full=true` it also truncates `users` and the catalog tables and re-runs the seed inserts. Runs in one transaction and returns `{ resetAt, full }`.
- Reset is API-only. The UI has no reset button so test suites own the lifecycle.

## 8. Error handling

- One `@ControllerAdvice` produces `{ "error": "<message>", "requestId": "<uuid>" }` for every error. Status mapping: validation 400, missing or invalid token 401, wrong role 403, unknown id 404, unhandled 500. The request id is also in the log line.
- Gateway unreachable during `POST /orders` returns 502. The order stays `PENDING` / `UNPAID` so the client can retry.
- Callback with a bad signature returns 401 and logs a warning. The order does not change.
- Cart validation rejects unknown ids, inactive pizzas, quantity below 1, and an empty item list.

## 9. Frontend changes

- Copy `src/app`, `src/components`, `src/contexts`, `src/types` and styling from the source project into `web/`. Rename the brand to Pizza Playground and drop client images and copy.
- Delete `src/lib/firebase.ts`, `src/lib/firestore/`, `src/lib/storage/`, `src/emails/`, the Firebase config files and all Next.js API routes.
- Add `src/lib/api.ts`, one fetch wrapper that attaches the JWT from `localStorage` and maps error bodies. Every page calls the API through it.
- Pages kept: home, menu, checkout, order confirmation, customer login and register, `orders/mine`, admin login, admin dashboard kanban, admin order history. Pages dropped: drinks, inventory, preorders, manual order, users, examples.
- `AdminContext` becomes an `AuthContext` that holds the token and user for both customers and admins.
- Environment: `NEXT_PUBLIC_API_URL` defaults to `http://localhost:8080`.

## 10. Repository layout

```
pizza-playground/
├── docker-compose.yml
├── .env.example
├── README.md
├── api/
│   ├── Dockerfile                      # multi-stage Maven build, JRE runtime
│   ├── pom.xml
│   └── src/main/java/…/{auth,catalog,orders,payments,admin,common}
│   └── src/main/resources/db/migration/{V1__schema.sql,V2__seed.sql}
├── web/
│   ├── Dockerfile
│   └── src/lib/api.ts
├── mock-gateway/
│   ├── Dockerfile
│   └── gateway-mock-soapui-project.xml
├── test-data/
│   └── customers.csv
└── .github/workflows/smoke.yml
```

## 11. Testing

- **API:** JUnit 5 with Testcontainers Postgres for order creation, callback handling, `autoPay`, reset, and role enforcement. MockMvc for validation and auth errors. One test per behaviour, no coverage target.
- **Web:** keep the Vitest unit tests that survive the Firebase removal (currency, cart pricing, cart operations), delete the rest.
- **Smoke (CI):** GitHub Actions runs `docker compose up -d`, waits on `/actuator/health`, logs in as a customer, places one `autoPay` order, calls reset, and tears down. This is the only job that proves the whole stack boots.
- KB-facing test scripts (JMeter plans, k6 scripts, Playwright specs) live in the KB, not in this repo.

## 12. Configuration

`.env.example` with working defaults:

```
POSTGRES_USER=playground
POSTGRES_PASSWORD=playground
POSTGRES_DB=playground
JWT_SECRET=change-me-but-it-works-as-is
GATEWAY_SIGNATURE_KEY=mock-signature-key
MOCK_GATEWAY_URL=http://mock-gateway:8090
PUBLIC_BASE_URL=http://localhost:3000
NEXT_PUBLIC_API_URL=http://localhost:8080
```

## 13. Deferred items

Each is an isolated later addition: drinks as a second item type, daily inventory limits with sold-out state, preorders, cashier manual orders, a mocked mail service, image uploads, refunds.
