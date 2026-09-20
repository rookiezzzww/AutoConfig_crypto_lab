#!/usr/bin/env sh
set -eu
base="${BASE_URL:-http://localhost:8080}"
echo "== /api/system/status =="
curl -fsS "$base/api/system/status"
echo
echo "== /api/scenarios/current =="
curl -fsS "$base/api/scenarios/current"
echo
echo "== /api/audit/logs (top) =="
curl -fsS "$base/api/audit/logs"
echo
