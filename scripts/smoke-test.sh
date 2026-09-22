#!/usr/bin/env sh
set -eu
base="${BASE_URL:-http://localhost:8080}"
get_current() { curl -fsS "$base/api/scenarios/current"; }
activate() {
  curl -fsS --max-time 1200 -X POST "$base/api/scenarios/$1/activate" \
    -H 'Content-Type: application/json' \
    -d "{\"options\":$2,\"stopPrevious\":true}"
}
curl -fsS "$base/api/scenarios" | grep -q 'heartbleed'
activate heartbleed '{"tlsProfile":"tls1_2"}' | grep -q '"success":true'
get_current | grep -q 'heartbleed'
activate poodle '{"protocol":"ssl3"}' | grep -q '"success":true'
get_current | grep -q 'poodle'
activate sweet32 '{"cipherSuite":"DES-CBC3-SHA"}' | grep -q '"success":true'
get_current | grep -q 'sweet32'
printf '%s\n' 'ALL TESTS PASSED'
