#!/usr/bin/env bash
# Plain curl calls for every endpoint, to copy one by one or run top to bottom.
# Usage: ./scripts/curl-examples.sh [base-url]      (default http://localhost:8080)
# Needs only curl. The courier id gets a time suffix so reruns start from a clean courier.
BASE="${1:-${BASE:-http://localhost:8080}}"
C="demo-$(date +%H%M%S)"
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EARLIER=$(date -u -r $(( $(date +%s) - 120 )) +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '2 minutes ago' +%Y-%m-%dT%H:%M:%SZ)
step() { printf '\n\033[1m# %s\033[0m\n' "$1"; }
# Prints the command with shell quoting (copy-pasteable), then runs it.
run()  { printf '$'; for a in "$@"; do case "$a" in *[!A-Za-z0-9_./:=@%+-]*) printf " '%s'" "$a" ;; *) printf ' %s' "$a" ;; esac; done; echo; "$@"; echo; }

step "Health"
run curl -s "$BASE/actuator/health"

step "1. List stores"
run curl -s "$BASE/api/v1/stores"

step "2. Send a location far from any store (outside) at $EARLIER"
run curl -s -X POST "$BASE/api/v1/locations" -H 'Content-Type: application/json' \
  -d "{\"courier\":\"$C\",\"time\":\"$EARLIER\",\"lat\":40.9960,\"lng\":29.1244229}"

step "3. Send a location 20 m from Ataşehir MMM Migros (store entrance) at $NOW"
run curl -s -X POST "$BASE/api/v1/locations" -H 'Content-Type: application/json' \
  -d "{\"courier\":\"$C\",\"time\":\"$NOW\",\"lat\":40.9925,\"lng\":29.1244229}"

step "4. Total distance of the courier"
run curl -s "$BASE/api/v1/couriers/$C/total-distance"

step "5. Store entrances of the courier"
run curl -s "$BASE/api/v1/couriers/$C/store-entrances"

step "6. Repeat step 3 (same time): ignored as stale, nothing changes"
run curl -s -X POST "$BASE/api/v1/locations" -H 'Content-Type: application/json' \
  -d "{\"courier\":\"$C\",\"time\":\"$NOW\",\"lat\":40.9925,\"lng\":29.1244229}"

step "7. Invalid latitude -> 400 (problem+json), with HTTP status"
run curl -s -w '\nHTTP %{http_code}' -X POST "$BASE/api/v1/locations" -H 'Content-Type: application/json' \
  -d "{\"courier\":\"$C\",\"time\":\"$NOW\",\"lat\":95,\"lng\":29}"

step "8. Unknown courier -> 404, with HTTP status"
run curl -s -w '\nHTTP %{http_code}' "$BASE/api/v1/couriers/nobody/total-distance"
