#!/usr/bin/env bash
# Walks through the interesting saga scenarios. Requires the platform to be running (make apps-up, or services from the IDE).
set -euo pipefail
API=${API:-http://localhost:8080/api/orders}

TOKEN=${TOKEN:-$("$(dirname "$0")/token.sh" demo-user)}   # the gateway requires a Bearer JWT
AUTH=(-H "Authorization: Bearer $TOKEN")

id_of()     { sed -E 's/.*"id":"([^"]+)".*/\1/'; }
field_of()  { sed -E "s/.*\"$1\":\"?([^\",}]*)\"?.*/\1/"; }

create() { # body [idempotency-key]
  local args=(-s "${AUTH[@]}" -X POST "$API" -H 'Content-Type: application/json' -d "$1")
  [[ -n "${2:-}" ]] && args+=(-H "Idempotency-Key: $2")
  curl "${args[@]}"
}

wait_final() { # order id -> prints the final order JSON
  for _ in $(seq 1 40); do
    local body status
    body=$(curl -s "${AUTH[@]}" "$API/$1"); status=$(echo "$body" | field_of status)
    if [[ "$status" == "CONFIRMED" || "$status" == "REJECTED" ]]; then echo "$body"; return; fi
    sleep 0.5
  done
  echo "timeout waiting for $1"; return 1
}

show() { echo "   -> status=$(echo "$1" | field_of status)  reason=$(echo "$1" | field_of failureReason)"; }

echo "1) Happy path: 2 phones x 100 = 200  (stock reserved -> payment ok -> CONFIRMED)"
id=$(create '{"customerId":"alice","items":[{"sku":"SKU-PHONE","quantity":2,"unitPrice":100.00}]}' | id_of)
show "$(wait_final "$id")"

echo "2) Out of stock: SKU-RARE has a single unit  (saga ends at step 1 -> REJECTED)"
id=$(create '{"customerId":"bob","items":[{"sku":"SKU-RARE","quantity":2,"unitPrice":10.00}]}' | id_of)
show "$(wait_final "$id")"

echo "3) Payment declined: 2 laptops x 600 = 1200 > limit 1000  (stock is RELEASED = compensation -> REJECTED)"
id=$(create '{"customerId":"carol","items":[{"sku":"SKU-LAPTOP","quantity":2,"unitPrice":600.00}]}' | id_of)
show "$(wait_final "$id")"

echo "4) Idempotency-Key: the same request twice creates ONE order"
body='{"customerId":"dave","items":[{"sku":"SKU-MOUSE","quantity":1,"unitPrice":20.00}]}'
a=$(create "$body" demo-key-$$ | id_of); b=$(create "$body" demo-key-$$ | id_of)
[[ "$a" == "$b" ]] && echo "   -> same order id both times ($a)" || { echo "   -> DIFFERENT ids: $a $b"; exit 1; }

echo "5) Security: no token -> $(curl -s -o /dev/null -w '%{http_code}' "$API/anything")  (expected 401)"

echo
echo "Now look at the evidence:"
echo "  Grafana  http://localhost:3000  (dashboard 'Order Platform - Overview', Explore > Tempo for the traces)"
echo "  Kafka UI http://localhost:8089  (topics inventory.commands / payment.commands / saga.replies / order.events)"
