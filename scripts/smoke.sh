#!/usr/bin/env bash
# End-to-end smoke test against a running `docker compose up` stack.
set -euo pipefail
API=${API:-http://localhost:8080}
WEB=${WEB:-http://localhost:3000}
json() { python3 -c "import sys,json;print(json.load(sys.stdin)[\"$1\"])"; }

for i in $(seq 1 60); do curl -fs "$API/actuator/health" | grep -q UP && break; sleep 2; done
curl -fs "$API/actuator/health" | grep -q UP || { echo "api not healthy"; exit 1; }
echo "api healthy"
MOCK=${MOCK:-http://localhost:8090}
# the mock has no health endpoint; an unknown bill returning 404 means it is serving
for i in $(seq 1 60); do [ "$(curl -s -o /dev/null -w '%{http_code}' "$MOCK/pay/ready")" = 404 ] && break; sleep 2; done
[ "$(curl -s -o /dev/null -w '%{http_code}' "$MOCK/pay/ready")" = 404 ] || { echo "mock gateway not up at $MOCK"; exit 1; }
echo "mock gateway up"

TOKEN=$(curl -fs -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"customer01@playground.local","password":"Password123!"}' | json token)
ORDER=$(curl -fs -X POST "$API/api/v1/orders" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"type":"PICKUP","customer":{"name":"Smoke","email":"customer01@playground.local","phone":"0100000000"},"items":[{"pizzaId":1,"sizeId":1,"crustId":1,"toppingIds":[],"quantity":1}],"autoPay":true}')
echo "$ORDER" | grep -q '"orderNumber"' || { echo "order failed: $ORDER"; exit 1; }
echo "autoPay order ok: $(echo "$ORDER" | json orderNumber)"

OWNER=$(curl -fs -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"owner@playground.local","password":"Password123!"}' | json token)
curl -fs -X POST "$API/api/v1/admin/reset" -H "Authorization: Bearer $OWNER" | grep -q resetAt
echo "reset ok"

# gateway round trip: create bill, pay on the mock, mock calls back the api
BILL=$(curl -fs -X POST "$API/api/v1/orders" -H 'Content-Type: application/json' \
  -d '{"type":"PICKUP","customer":{"name":"Smoke","email":"g@x.local","phone":"1"},"items":[{"pizzaId":1,"sizeId":1,"crustId":1,"toppingIds":[],"quantity":1}],"autoPay":false}')
ORDER_ID=$(echo "$BILL" | json orderId)
PAY_URL=$(echo "$BILL" | json payUrl)
curl -fs -o /dev/null -X POST -d action=pay "$PAY_URL"
for i in $(seq 1 10); do
  curl -fs "$API/api/v1/payments/$ORDER_ID/status" | grep -q '"PAID"' && break; sleep 1
done
curl -fs "$API/api/v1/payments/$ORDER_ID/status" | grep -q '"PAID"' || { echo "callback did not mark order paid"; exit 1; }
echo "gateway callback ok"

for i in $(seq 1 30); do curl -fs -o /dev/null "$WEB/menu" && break; sleep 2; done
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$WEB/menu")
[ "$CODE" = 200 ] || { echo "web /menu returned $CODE"; exit 1; }
echo "web /menu 200"
echo "SMOKE OK"
