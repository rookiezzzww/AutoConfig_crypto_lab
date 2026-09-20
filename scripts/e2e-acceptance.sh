#!/usr/bin/env sh
set -eu

BASE_URL="${BASE_URL:-http://localhost:8080}"
ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    exit 1
  }
}

require_cmd docker
require_cmd curl

cd "$ROOT_DIR"

echo "[1/8] starting services"
docker compose up -d --build

echo "[2/8] waiting control-plane API"
for i in $(seq 1 120); do
  if curl -fsS "$BASE_URL/api/system/status" >/dev/null 2>&1; then
    break
  fi
  if [ "$i" -eq 120 ]; then
    echo "control-plane API did not become ready in time" >&2
    exit 1
  fi
  sleep 2
done

for svc in crypto-control crypto-haproxy crypto-heartbleed crypto-poodle crypto-sweet32; do
  if [ -z "$(docker ps -q -f name=^/${svc}$)" ]; then
    echo "service is not running: $svc" >&2
    exit 1
  fi
done

cp_started_before="$(docker inspect -f '{{.State.StartedAt}}' crypto-control)"
ha_started_before="$(docker inspect -f '{{.State.StartedAt}}' crypto-haproxy)"

get_current() {
  curl -fsS "$BASE_URL/api/scenarios/current"
}

assert_current() {
  id="$1"
  get_current | grep -q "\"id\":\"$id\"" || {
    echo "current scenario is not $id" >&2
    get_current >&2
    exit 1
  }
}

activate() {
  id="$1"
  curl -fsS -X POST "$BASE_URL/api/scenarios/$id/activate" | grep -q '"success":true' || {
    echo "failed to activate $id" >&2
    exit 1
  }
}

echo "[3/8] default active scenario"
assert_current heartbleed

echo "[4/8] switch heartbleed -> poodle"
activate poodle
assert_current poodle

echo "[5/8] switch poodle -> sweet32"
activate sweet32
assert_current sweet32

echo "[6/8] switch sweet32 -> heartbleed"
activate heartbleed
assert_current heartbleed

echo "[7/8] verify audit trail"
audit_payload="$(curl -fsS "$BASE_URL/api/audit/logs")"
echo "$audit_payload" | grep -q '"previousScenario":"heartbleed","targetScenario":"poodle"' || {
  echo "audit log missing heartbleed -> poodle" >&2
  exit 1
}
echo "$audit_payload" | grep -q '"previousScenario":"poodle","targetScenario":"sweet32"' || {
  echo "audit log missing poodle -> sweet32" >&2
  exit 1
}

echo "[8/8] verify no restart"
cp_started_after="$(docker inspect -f '{{.State.StartedAt}}' crypto-control)"
ha_started_after="$(docker inspect -f '{{.State.StartedAt}}' crypto-haproxy)"
[ "$cp_started_before" = "$cp_started_after" ] || { echo "control-plane restarted unexpectedly" >&2; exit 1; }
[ "$ha_started_before" = "$ha_started_after" ] || { echo "haproxy restarted unexpectedly" >&2; exit 1; }

echo "ALL ACCEPTANCE TESTS PASSED"
