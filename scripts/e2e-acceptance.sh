#!/usr/bin/env sh
set -eu

BASE_URL="${BASE_URL:-http://localhost:8080}"
ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"

command -v docker >/dev/null 2>&1 || { echo "missing required command: docker" >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "missing required command: curl" >&2; exit 1; }
cd "$ROOT_DIR"

assert_current() {
  expected="$1"
  curl -fsS "$BASE_URL/api/scenarios/current" | grep -q "\"id\":\"$expected\"" || {
    echo "current scenario is not $expected" >&2
    exit 1
  }
}

activate() {
  id="$1"
  options="$2"
  curl -fsS --max-time 1200 -X POST "$BASE_URL/api/scenarios/$id/activate" \
    -H 'Content-Type: application/json' \
    -d "{\"options\":$options,\"stopPrevious\":true}" | grep -q '"success":true' || {
      echo "failed to activate $id" >&2
      exit 1
    }
}

echo "[1/8] reset and start control services only"
docker compose down
docker compose up -d --build

echo "[2/8] wait for control-plane API"
for i in $(seq 1 120); do
  curl -fsS "$BASE_URL/api/system/status" >/dev/null 2>&1 && break
  [ "$i" -eq 120 ] && { echo "control-plane API did not become ready" >&2; exit 1; }
  sleep 2
done

for service in crypto-control crypto-haproxy; do
  [ -n "$(docker ps -q -f name=^/${service}$)" ] || { echo "service is not running: $service" >&2; exit 1; }
done
for scenario in crypto-heartbleed crypto-poodle crypto-sweet32; do
  [ -z "$(docker ps -q -f name=^/${scenario}$)" ] || { echo "scenario was prestarted: $scenario" >&2; exit 1; }
done
if curl -fsS "$BASE_URL/api/scenarios/current" | grep -q '"id":'; then
  echo "unexpected active scenario" >&2
  exit 1
fi

cp_started_before="$(docker inspect -f '{{.State.StartedAt}}' crypto-control)"
ha_started_before="$(docker inspect -f '{{.State.StartedAt}}' crypto-haproxy)"

echo "[3/8] initialize heartbleed on demand"
activate heartbleed '{"tlsProfile":"tls1_2"}'
assert_current heartbleed

echo "[4/8] switch to poodle and stop heartbleed"
activate poodle '{"protocol":"ssl3"}'
assert_current poodle

echo "[5/8] switch to sweet32 and stop poodle"
activate sweet32 '{"cipherSuite":"DES-CBC3-SHA"}'
assert_current sweet32

echo "[6/8] verify previous containers are stopped"
[ "$(docker inspect -f '{{.State.Running}}' crypto-heartbleed)" = false ] || { echo "heartbleed should be stopped" >&2; exit 1; }
[ "$(docker inspect -f '{{.State.Running}}' crypto-poodle)" = false ] || { echo "poodle should be stopped" >&2; exit 1; }

echo "[7/8] verify audit trail"
curl -fsS "$BASE_URL/api/audit/logs" | grep -q '"previousScenario":"heartbleed","targetScenario":"poodle"' || {
  echo "audit log missing heartbleed -> poodle" >&2
  exit 1
}

echo "[8/8] verify control services did not restart"
[ "$cp_started_before" = "$(docker inspect -f '{{.State.StartedAt}}' crypto-control)" ] || { echo "control-plane restarted" >&2; exit 1; }
[ "$ha_started_before" = "$(docker inspect -f '{{.State.StartedAt}}' crypto-haproxy)" ] || { echo "haproxy restarted" >&2; exit 1; }

echo "ALL ACCEPTANCE TESTS PASSED"
