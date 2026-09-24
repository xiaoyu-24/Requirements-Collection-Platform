#!/usr/bin/env bash
set -euo pipefail

HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/api/health}"
response="$(curl --fail --silent --show-error --max-time 10 "$HEALTH_URL")"

if [[ "$response" != *'"status":"UP"'* ]]; then
  echo "Unexpected health response: $response" >&2
  exit 1
fi

echo "Backend is healthy: $response"
