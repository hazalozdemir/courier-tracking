#!/usr/bin/env bash
# Shared helpers for the scripts. Source it, do not run it.
# Only needs bash, curl and sed (no jq).

BASE="${BASE:-http://localhost:8080}"
API="$BASE/api/v1"

if [[ -t 1 ]]; then
  GREEN=$'\e[32m'; RED=$'\e[31m'; YELLOW=$'\e[33m'; BOLD=$'\e[1m'; RESET=$'\e[0m'
else
  GREEN=""; RED=""; YELLOW=""; BOLD=""; RESET=""
fi

# Performs a request and sets STATUS (HTTP code) and BODY.
#   request GET  /couriers/c1/total-distance
#   request POST /locations '{"courier":...}'
request() {
  local method="$1" path="$2" data="${3:-}" out
  if [[ -n "$data" ]]; then
    out=$(curl -sS -X "$method" "$API$path" -H 'Content-Type: application/json' -d "$data" -w $'\n%{http_code}')
  else
    out=$(curl -sS -X "$method" "$API$path" -w $'\n%{http_code}')
  fi
  STATUS="${out##*$'\n'}"
  BODY="${out%$'\n'*}"
}

# Sends one location for a courier: post_location <courier> <time> <lat> <lng>
post_location() {
  request POST /locations "{\"courier\":\"$1\",\"time\":\"$2\",\"lat\":$3,\"lng\":$4}"
}

# Extracts a scalar field from a flat JSON object in BODY: json_field status
json_field() {
  printf '%s' "$BODY" | sed -n "s/.*\"$1\":\"\{0,1\}\([^\",}]*\)\"\{0,1\}.*/\1/p" | head -1
}

# Counts occurrences of a key in BODY (e.g. number of objects in an array).
json_count() {
  printf '%s' "$BODY" | grep -o "\"$1\":" | wc -l | tr -d ' '
}

wait_for_health() {
  local timeout="${1:-90}" i
  for ((i = 0; i < timeout; i++)); do
    if curl -sf "$BASE/actuator/health" 2>/dev/null | grep -q '"UP"'; then return 0; fi
    sleep 1
  done
  return 1
}
