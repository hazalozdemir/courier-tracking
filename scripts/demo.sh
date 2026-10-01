#!/usr/bin/env bash
# Guided demo: walks through what the service does, step by step, with explanations.
# Starts a throwaway instance if none is running and stops it at the end.
# Usage: ./scripts/demo.sh [--auto] [base-url]
#   --auto  do not wait for Enter between chapters (short delays instead)
set -uo pipefail
cd "$(dirname "$0")/.."

AUTO=false
[[ "${1:-}" == "--auto" ]] && { AUTO=true; shift; }
[[ -t 0 ]] || AUTO=true
export BASE="${1:-${BASE:-http://localhost:8080}}"
source scripts/lib.sh
export LC_ALL="${LC_ALL:-en_US.UTF-8}"   # so ${#s} counts characters, not bytes (ş, ö, ...)
DIM=$'\e[2m'; CYAN=$'\e[36m'; [[ -t 1 ]] || { DIM=""; CYAN=""; }

chapter() {
  if [[ "$AUTO" == true ]]; then sleep 1
  else printf '\n%s[Enter] to continue...%s' "$DIM" "$RESET"; read -r _; fi
  printf '\n%s━━━ %s ━━━%s\n' "$BOLD$CYAN" "$1" "$RESET"
}
say()  { printf '%s\n' "$1"; }
note() { printf '%s  %s%s\n' "$DIM" "$1" "$RESET"; }
pad()  { printf '%s%*s' "$1" $(($2 - ${#1})) ""; }   # printf %-Ns pads by bytes

# Event times are relative to now so the demo looks live and stays within the clock-skew limit.
T0=$(( $(date +%s) - 3600 ))
at() {
  local s=$((T0 + $1))
  date -u -r "$s" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d "@$s" +%Y-%m-%dT%H:%M:%SZ
}
clock() { at "$1" | cut -c12-19; }

# ping <courier> <seconds after T0> <lat> <lng> <what the courier is doing>
ping() {
  local courier="$1" t="$2" lat="$3" lng="$4" what="$5" status added stores
  post_location "$courier" "$(at "$t")" "$lat" "$lng"
  status=$(json_field status)
  added=$(json_field distanceAddedMeters)
  stores=$(printf '%s' "$BODY" | sed -n 's/.*"enteredStores":\[\(.*\)\].*/\1/p' | tr -d '"')
  printf '  %s%s%s  %s %s%s%s  %s ' "$DIM" "$(clock "$t")" "$RESET" "$(pad "$courier" 13)" \
    "$DIM" "$(pad "lat $lat" 15) $(pad "lng $lng" 14)" "$RESET" "$(pad "$what" 44)"
  if [[ "$STATUS" != 200 ]]; then
    printf '%s✗ HTTP %s%s\n' "$RED" "$STATUS" "$RESET"
  elif [[ "$status" == IGNORED_STALE ]]; then
    printf '%s⟲ ignored (not newer than last ping)%s\n' "$YELLOW" "$RESET"
  elif [[ -n "$stores" ]]; then
    printf '%s✔ ENTRANCE: %s%s %s+%.0f m%s\n' "$GREEN$BOLD" "$stores" "$RESET" "$DIM" "$added" "$RESET"
  else
    printf '%s· no entrance  +%.0f m%s\n' "$DIM" "$added" "$RESET"
  fi
  [[ "$AUTO" == true ]] && sleep 0.3
}

# ---------------------------------------------------------------------------------------------
STARTED_HERE=false
if ! curl -sf "$BASE/actuator/health" >/dev/null 2>&1; then
  BASE="http://localhost:18080"; API="$BASE/api/v1"
  say "No app running, starting a fresh instance on $BASE ..."
  scripts/start.sh --fresh --port 18080 >/dev/null || { echo "Could not start the app, see target/app.log"; exit 1; }
  STARTED_HERE=true
  trap 'scripts/stop.sh >/dev/null' EXIT
fi
LOG_LINES_BEFORE=$( [[ -f target/app.log ]] && wc -l <target/app.log || echo 0 )
RUN=$(date +%H%M%S)
ALI="ali-$RUN"; AYSE="ayse-$RUN"; MEHMET="mehmet-$RUN"

printf '\n%sCourier Tracking — live demo%s  (%s)\n' "$BOLD" "$RESET" "$BASE"
say "Couriers stream their GPS position (time, courier, lat, lng) to the service. It"
say "  1) logs a store entrance when a courier comes within 100 m of a Migros store,"
say "  2) ignores re-entries to the same store within 1 minute,"
say "  3) keeps each courier's total travel distance."

chapter "1. Stores the service watches"
request GET /stores
printf '%s\n' "$BODY" | sed 's/},{/}\n{/g' | sed -n 's/.*"name":"\([^"]*\)".*"lat":\([0-9.]*\).*"lng":\([0-9.]*\).*/\1|\2|\3/p' |
  while IFS='|' read -r name lat lng; do printf '  📍 %s %s, %s\n' "$(pad "$name" 24)" "$lat" "$lng"; done
note "Loaded once at startup from stores.json. Each store has a 100 m entrance radius."

chapter "2. Ali's shift around Ataşehir"
say "Ali drives towards Ataşehir MMM Migros, drops off an order, circles the block and comes back."
echo
ping "$ALI"   0 40.9960 29.1244229 "400 m north of Ataşehir, on the way"
ping "$ALI"  30 40.9925 29.1244229 "arrives at Ataşehir (20 m from the store)"
ping "$ALI"  40 40.9924 29.1244229 "waiting in front of the store"
note "Staying inside the circle is not a new entrance, only outside → inside counts."
ping "$ALI"  50 40.9960 29.1244229 "drives away"
ping "$ALI"  70 40.9925 29.1244229 "comes back 40 s after the entrance"
note "Same store within 1 minute → not counted again (GPS jitter / circling the block)."
ping "$ALI"  80 40.9960 29.1244229 "drives away again"
ping "$ALI" 120 40.9925 29.1244229 "comes back 90 s after the entrance"
note "More than 1 minute since the last counted entrance → counted."
ping "$ALI" 600 40.9863 29.1161    "drives on to Novada MMM Migros"

chapter "3. Unreliable networks: late and duplicate pings"
say "Phones retry and packets arrive late. Pings are ordered by the device time, not arrival time."
echo
ping "$ALI" 100 41.0000 29.0000    "a delayed ping from 8 minutes ago arrives"
ping "$ALI" 600 40.9863 29.1161    "the last ping is retried (duplicate)"
note "Neither changes the distance or creates an entrance, so retries are safe."

chapter "4. More couriers across Istanbul at the same time"
say "Ayşe works on the European side, Mehmet on the Anatolian side. Their pings arrive interleaved."
echo
ping "$AYSE"     0 41.0600 29.0250 "leaving Beşiktaş"
ping "$MEHMET"   0 40.9700 29.0700 "leaving Kadıköy"
ping "$AYSE"   300 41.0560 29.0212 "arrives at Ortaköy"
ping "$MEHMET" 240 40.9634 29.0632 "arrives at Caddebostan"
ping "$MEHMET" 270 40.9632 29.0630 "next order at Caddebostan"
note "Mehmet already entered Caddebostan 30 s ago and never left, so no new entrance."
ping "$AYSE"  2400 41.0068 28.6553 "drives out to Beylikdüzü"
ping "$MEHMET" 900 40.9923 29.1244 "drives to Ataşehir"
note "Each courier has its own timeline: Mehmet's 15 min ping is not 'late' just because Ayşe is at 40 min."

chapter "5. Results"
for c in "$ALI" "$AYSE" "$MEHMET"; do
  request GET "/couriers/$c/total-distance"
  km=$(json_field totalDistanceKilometers)
  request GET "/couriers/$c/store-entrances"
  printf '\n  %s%s%s — total distance %s%.2f km%s, %s store entrance(s)\n' \
    "$BOLD" "$c" "$RESET" "$GREEN" "$km" "$RESET" "$(json_count storeName)"
  printf '%s\n' "$BODY" | sed 's/},{/}\n{/g' |
    sed -n 's/.*"storeName":"\([^"]*\)".*"enteredAt":"\([^"]*\)".*/\2|\1/p' |
    while IFS='|' read -r t name; do printf '    %s  %s\n' "${t:11:8}" "$name"; done
done
echo
note "GET /api/v1/couriers/{id}/total-distance and /store-entrances"

if [[ -f target/app.log ]]; then
  chapter "6. Server log (Observer pattern)"
  say "Every counted entrance is published as an event; a listener logs it after the transaction commits:"
  echo
  tail -n +"$((LOG_LINES_BEFORE + 1))" target/app.log | grep "entered store" | grep -E "$ALI|$AYSE|$MEHMET" |
    sed 's/.*StoreEntranceLogger *: /  /'
fi

chapter "7. Invalid input is rejected with a clear error"
note "POST /api/v1/locations  {\"courier\":\"$ALI\",\"time\":\"...\",\"lat\":95,\"lng\":29}"
post_location "$ALI" "$(at 700)" 95 29
printf '  HTTP %s  %s\n' "$STATUS" "$(json_field detail)"
request GET "/couriers/nobody-$RUN/total-distance"
note "GET /api/v1/couriers/nobody-$RUN/total-distance"
printf '  HTTP %s  %s\n' "$STATUS" "$(json_field detail)"

printf '\n%sDemo finished.%s' "$BOLD" "$RESET"
if [[ "$STARTED_HERE" == true ]]; then
  say " The temporary instance will now be stopped."
else
  say " Try it yourself: $BASE/swagger-ui.html"
fi
