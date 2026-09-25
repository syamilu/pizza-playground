# Mock payment gateway (SoapUI)

A SoapUI open-source REST MockService that stands in for a real payment gateway. The API creates a bill here, the browser pays (or fails) on a hosted page, and the mock sends a signed callback to the API.

- Project file: `gateway-mock-soapui-project.xml` (MockService `PaymentGatewayMock`, port 8090)
- Image: SoapUI 5.7.2 `mockservicerunner.sh` on `eclipse-temurin:11-jre`. It has to be Java 11: SoapUI 5.7.2 ships Groovy 3.0.6, which fails on Java 17 with `Unsupported class file major version 61`.

## Run

```bash
docker compose up mock-gateway            # as part of the stack
# or on its own:
docker build -t pizza-mock mock-gateway
docker run --rm -p 8090:8090 -e API_CALLBACK_URL=http://host.docker.internal:9999/cb pizza-mock
```

| Env var | Default | Purpose |
|---|---|---|
| `GATEWAY_SIGNATURE_KEY` | `mock-signature-key` | HMAC key. Must match the API's key. |
| `API_CALLBACK_URL` | the bill's `callbackUrl` | Where the signed callback is POSTed. Compose sets `http://api:8080/api/v1/payments/callback`. |

## Endpoints

| Method and path | Behaviour |
|---|---|
| `POST /bills` | JSON `{amount, orderId, callbackUrl, redirectUrl}` → 200 `{"id":"bill_<first 8 of orderId>_<epoch ms>","url":"http://localhost:8090/pay/<id>"}` |
| `GET /pay/{billId}` | HTML pay page showing the order id and amount, with `<button id="pay-button">` and `<button id="fail-button">` |
| `POST /pay/{billId}` | Form field `action=pay` or `action=fail`. Sends the callback, then replies 302 to the bill's `redirectUrl`. If the callback fails or returns non-200, the mock logs it and still redirects. |
| Unknown bill id | 404 `{"error":"unknown bill"}` |

Bills live in memory (a `ConcurrentHashMap` stored under the JVM system property `pizza.bills`, so all three actions share it). A restart clears them.

### Callback

`POST $API_CALLBACK_URL`, `Content-Type: application/x-www-form-urlencoded`, fields `amount, id, order_id, paid, paid_at, x_signature`.

Signature: HMAC-SHA256 with `GATEWAY_SIGNATURE_KEY`, lowercase hex, over this string (keys sorted, `|` separator, no trailing separator):

```
amount=19.00|id=bill_11111111_1790266442139|order_id=11111111-2222-3333-4444-555555555555|paid=true|paid_at=2026-09-24T16:14:02Z
```

`amount` always has two decimals. `paid` is `true` or `false`. `paid_at` is an ISO instant, accurate to the second. To check a signature by hand:

```bash
printf '%s' 'amount=19.00|id=...|order_id=...|paid=true|paid_at=...' | openssl dgst -sha256 -hmac 'mock-signature-key'
```

## Slow gateway: `X-Mock-Delay`

Send `X-Mock-Delay: <ms>` on any request and the mock sleeps that long before answering. The cap is 30000 ms. Use it for timeout tests and for load tests that need a slow third party. The delay lives in the MockService's `OnRequest` script, so it covers every path, 404s included.

```bash
time curl -s -H 'X-Mock-Delay: 1500' localhost:8090/pay/nope   # about 1.5 s
```

## Open and edit in SoapUI desktop

1. Install SoapUI Open Source 5.7.x and choose **File → Import Project** → `mock-gateway/gateway-mock-soapui-project.xml`.
2. Expand **PaymentGatewayMock**. It has three actions: `Create bill` (POST /bills), `Pay page` (GET /pay) and `Pay submit` (POST /pay).
3. Double-click the MockService and press the green ▶ to run it locally on port 8090. When `API_CALLBACK_URL` is not set, the callback goes to the `callbackUrl` in the bill request.

How the mock is put together, so you know where to make changes:

- **Routing.** SoapUI REST mocks match on method plus path prefix, with no `{param}` templates. `/pay` therefore also matches `/pay/<billId>`, and the scripts read the bill id from the last path segment.
- **Logic** lives in each action's **Dispatch** script (dispatch style `SCRIPT`). The script does the work, puts values into `requestContext`, and returns the name of the response to send.
- **Responses** are ordinary SoapUI mock responses. Each has a fixed HTTP status and a body such as `${body}`, `${page}` or a static JSON string. The redirect response sets the header `Location: ${location}`. To change a status code, body or header, edit the response. To add a scenario (for example a 500 from `/bills`), add a response and have the dispatch script return its name.
- **Save** in SoapUI writes back to the same XML file. Rebuild the image (`docker compose build mock-gateway`) to pick up the change.


## Known behaviour

- Bills live in the mock's memory. Restarting or rebuilding the `mock-gateway` container forgets every unpaid bill; the API order stays PENDING and can be cleared with the owner reset.
- SoapUI's embedded Jetty defaults to a 4 KB request-header buffer, which a browser with a few `localhost` cookies overflows (HTTP 413). The mock service's start script raises it to 64 KB.
