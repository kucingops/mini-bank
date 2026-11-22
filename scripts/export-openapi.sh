#!/usr/bin/env bash
# Export the OpenAPI spec of each running service into docs/openapi/<service>.json.
#
# Usage:
#   docker compose up -d --build
#   ./scripts/export-openapi.sh
#
# CI runs this and fails if the committed specs differ, so every API change
# shows up in the pull request diff. Commit the regenerated files with your change.
set -euo pipefail

cd "$(dirname "$0")/.."
OUT=docs/openapi
TIMEOUT=${OPENAPI_WAIT_SECONDS:-180}
mkdir -p "$OUT"

declare -A SERVICES=(
  [account-service]=8081
  [transaction-service]=8082
  [fraud-service]=8083
)

for service in account-service transaction-service fraud-service; do
  url="http://localhost:${SERVICES[$service]}/v3/api-docs"
  echo "Waiting for $url ..."
  deadline=$((SECONDS + TIMEOUT))
  until curl -sf "$url" -o /dev/null; do
    if (( SECONDS > deadline )); then
      echo "Timed out waiting for $service" >&2
      exit 1
    fi
    sleep 3
  done
  # jq -S sorts keys so the output is stable between runs
  curl -sf "$url" | jq -S . > "$OUT/$service.json"
  echo "Wrote $OUT/$service.json"
done
