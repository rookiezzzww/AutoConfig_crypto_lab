#!/usr/bin/env sh
set -eu
base="${BASE_URL:-http://localhost:8080}"
get_current() { curl -fsS "$base/api/scenarios/current"; }
activate() { curl -fsS -X POST "$base/api/scenarios/$1/activate"; }
curl -fsS "$base/api/scenarios" | grep -q 'heartbleed'
activate heartbleed | grep -q '"success":true'
get_current | grep -q 'heartbleed'
activate poodle | grep -q '"success":true'
get_current | grep -q 'poodle'
activate sweet32 | grep -q '"success":true'
get_current | grep -q 'sweet32'
printf '%s\n' 'ALL TESTS PASSED'
