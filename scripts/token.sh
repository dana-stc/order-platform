#!/usr/bin/env bash
# Prints a dev JWT (HS256) accepted by the gateway. Usage: scripts/token.sh [subject] [ttl-seconds]
# Uses the same default secret as api-gateway (JWT_SECRET). In real environments tokens come from your IdP.
set -euo pipefail
SUB=${1:-demo-user}; TTL=${2:-3600}; SECRET=${JWT_SECRET:-dev-only-secret-change-me-0123456789abcdef}
b64() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
H=$(printf '{"alg":"HS256","typ":"JWT"}' | b64)
P=$(printf '{"sub":"%s","iat":%s,"exp":%s}' "$SUB" "$(date +%s)" "$(( $(date +%s) + TTL ))" | b64)
S=$(printf '%s.%s' "$H" "$P" | openssl dgst -binary -sha256 -hmac "$SECRET" | b64)
echo "$H.$P.$S"
