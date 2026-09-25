# Pizza Playground

A self-contained pizza ordering app you run locally with one `docker compose up`. It is the practice target for the Testing Knowledge Base tracks (JMeter, k6 and Playwright). The stack has a Next.js shop and admin UI, a Spring Boot API, Postgres, and a SoapUI mock payment gateway, so it needs no cloud accounts. Every test run can start from the same state with one API call. The full design is in [`docs/superpowers/specs/2026-09-24-pizza-playground-design.md`](docs/superpowers/specs/2026-09-24-pizza-playground-design.md).

## Run

```bash
cp .env.example .env
docker compose up --build
```

The first build takes several minutes. Maven downloads the API's dependencies, the web image runs `npm ci` and `next build`, and the mock image downloads SoapUI. Later builds use the cache. The defaults in `.env.example` work as they are.

Stop the stack and wipe the database with `docker compose down -v`.

## URLs

| What | URL |
|---|---|
| Shop | http://localhost:3000 |
| Staff login (admin kanban) | http://localhost:3000/admin/login |
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |
| Mock payment gateway | http://localhost:8090 |
| Postgres | `localhost:5432`, database, user and password `playground` |

## Test accounts

Every account's password is `Password123!`.

| Email | Role | Can do |
|---|---|---|
| `owner@playground.local` | OWNER | Everything, including order history and reset |
| `cashier@playground.local` | CASHIER | Kanban: list orders and change their status |
| `customer01@playground.local` … `customer20@playground.local` | CUSTOMER | Order and see their own orders |

`test-data/customers.csv` holds the twenty customer logins (`email,password`) for JMeter and k6 CSV data sets. Guest checkout works too: `POST /orders` without a token.

## Reset between runs

`POST /api/v1/admin/reset` (OWNER only) truncates orders, order items and payments, and restarts the order number at 1001. Add `?full=true` to also re-seed users and the menu. It returns `{ "resetAt": "...", "full": false }`.

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"owner@playground.local","password":"Password123!"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
curl -s -X POST localhost:8080/api/v1/admin/reset -H "Authorization: Bearer $TOKEN"
curl -s -X POST 'localhost:8080/api/v1/admin/reset?full=true' -H "Authorization: Bearer $TOKEN"
```

The UI has no reset button, so each test suite controls its own setup and cleanup.

## For load testers

- **`autoPay: true`** on `POST /api/v1/orders` skips the gateway. The order is created as `NEW` / `PAID` in one transaction and `payUrl` is `null`. Use it to push order volume without the mock in the loop.

  ```json
  {
    "type": "PICKUP",
    "customer": { "name": "Load", "email": "customer01@playground.local", "phone": "0100000000" },
    "items": [{ "pizzaId": 1, "sizeId": 1, "crustId": 1, "toppingIds": [], "quantity": 1 }],
    "autoPay": true
  }
  ```

- **Gateway round trip.** With `autoPay: false` the response carries `payUrl` (`http://localhost:8090/pay/<billId>`). `POST` it with form field `action=pay` or `action=fail`. The mock sends a signed callback to the API and replies 302 to the confirmation page. Poll `GET /api/v1/payments/{orderId}/status` until it is `PAID` or `FAILED`.
- **`X-Mock-Delay: <ms>`** makes the mock sleep that long (cap 30000 ms). It works on any request sent to the mock directly, and on `POST /api/v1/orders` (with `autoPay: false`), where the API forwards it to the mock's bill call. Use it to simulate a slow third party. The API gives up on the mock after 10 s and answers 502.
- **Order numbers** come from a sequence that starts at 1001. Reset restarts it, so after a reset the first order is always #1001.
- **Callback signature.** To post callbacks yourself, see [`mock-gateway/README.md`](mock-gateway/README.md). It covers the fields, the HMAC-SHA256 string format, and how to check a signature with `openssl`.
- Prices are computed on the server from ids. The client never sends a price.

## For UI testers

Customers log in at `/login`. Staff (owner and cashier) log in at **`/admin/login`**. A non-admin who opens any other `/admin/*` page is sent to `/admin/login`. A customer who logs in there sees `admin-login-error`.

The kanban polls every **5 s**. To see a new order straight away, click `refresh-orders` instead of waiting.

On the menu, the card itself does not open the customizer. Click `customize` inside a `menu-pizza-card`. `cart-count` only renders when the cart has something in it.

Stable `data-testid` values, by page:

| Page | Test ids |
|---|---|
| Login and register (`/login`, `/register`, `/admin/login`) | `login-form`, `register-form`, `auth-error`, `admin-login-error`, `nav-user` |
| Home and menu (`/`, `/menu`) | `hero`, `home-error`, `menu-error`, `menu-pizza-card`, `customize` |
| Customizer (dialog) | `customizer-size-<id>`, `customizer-crust-<id>`, `customizer-topping-<id>`, `customizer-quantity`, `customizer-total`, `add-to-cart` |
| Cart (navbar drawer) | `cart-open`, `cart-count`, `cart-line`, `cart-total`, `cart-checkout` |
| Checkout (`/checkout`) | `checkout-form`, `checkout-name`, `checkout-email`, `checkout-phone`, `checkout-type-PICKUP`, `checkout-type-DELIVERY`, `checkout-total`, `checkout-submit`, `checkout-error` |
| Mock pay page (`localhost:8090/pay/<billId>`) | `#pay-button`, `#fail-button` (element ids, not test ids) |
| Confirmation (`/order-confirmation?orderId=`) | `payment-status`, `order-number`, `order-items`, `order-total`, `payment-failed`, `payment-timeout`, `payment-refresh`, `order-not-found` |
| My orders (`/orders`) | `my-order`, `orders-error` |
| Admin header | `admin-user`, `admin-logout` |
| Kanban (`/admin/dashboard`) | `kanban-column-NEW`, `kanban-column-PREPARING`, `kanban-column-READY`, `order-card` (with `data-order-number`), `advance-status`, `cancel-order`, `refresh-orders` |
| Order history (`/admin/order-history`, OWNER) | `history-from`, `history-to`, `history-load`, `history-table`, `history-row` (with `data-order-number`), `history-forbidden`, `history-error` |

## API summary

Base path `/api/v1`. Full schemas are in Swagger UI. Errors look like `{ "error": "...", "requestId": "..." }`.

| Endpoint | Auth | Purpose |
|---|---|---|
| `POST /auth/register` | none | Create a CUSTOMER account |
| `POST /auth/login` | none | Returns `{ token, user }` (JWT, 12 h) |
| `GET /auth/me` | any token | Current user |
| `GET /menu` | none | Pizzas with sizes, crusts and toppings |
| `POST /orders` | optional token | Price the cart and create the order. Returns `{ orderId, orderNumber, payUrl }` |
| `GET /orders/{id}` | none (UUID) | One order |
| `GET /orders/mine` | CUSTOMER | The caller's orders |
| `POST /payments/callback` | HMAC signature | Gateway webhook |
| `GET /payments/{orderId}/status` | none | Payment status |
| `GET /admin/orders?status=` | OWNER, CASHIER | Active orders |
| `PATCH /admin/orders/{id}/status` | OWNER, CASHIER | Move an order along the board or cancel it |
| `GET /admin/orders/history?from=&to=` | OWNER | Orders in a date range (UTC days) |
| `POST /admin/reset[?full=true]` | OWNER | Reset the data |
| `GET /actuator/health` (no `/api/v1`) | none | Health check |

## Repo layout

```
pizza-playground/
├── docker-compose.yml        four services: db, api, mock-gateway, web
├── .env.example              working defaults
├── api/                      Spring Boot 3, Java 21, Flyway migrations and seed
├── web/                      Next.js 15 shop and admin UI
├── mock-gateway/             SoapUI mock payment gateway (project XML and Dockerfile)
├── test-data/customers.csv   customer logins for load tests
├── scripts/smoke.sh          end-to-end smoke test against a running stack
├── .github/workflows/        CI: boots the stack and runs the smoke test
└── docs/                     design spec
```

## Running tests

```bash
cd api && mvn test            # JUnit and Testcontainers (needs Docker)
cd web && npm test -- --run   # Vitest unit tests
bash scripts/smoke.sh         # against a running `docker compose up` stack
```

The smoke script waits for the API and the mock, logs in, places an `autoPay` order, resets as owner, does a full gateway pay round trip, and checks that the shop serves `/menu`. CI runs the same script on every push and pull request.

## Known limitations

- Not built yet: drinks, inventory limits and sold-out state, preorders, cashier manual orders, real email (the API only logs "would send email"), image uploads, refunds, rate limiting.
- No HTTPS. Everything is plain HTTP on localhost.
- CORS allows only `http://localhost:3000`. If you serve the shop from another origin, browser calls to the API fail.
- One shared JWT secret (`JWT_SECRET`) and no refresh tokens.
- The mock gateway returns pay URLs hard-coded to `http://localhost:8090`, so the pay page only works from the machine running the stack.
- Every compose port binds on all interfaces and the stack ships well-known default secrets (`JWT_SECRET`, `GATEWAY_SIGNATURE_KEY`, `POSTGRES_PASSWORD`). Run it on a trusted network, or change the secrets in `.env`.
- `NEXT_PUBLIC_API_URL` is baked into the web image at build time. Rebuild `web` after changing it.
