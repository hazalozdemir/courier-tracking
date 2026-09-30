#!/usr/bin/env bash
# Builds (if needed) and starts the app in the background, then waits until it is healthy.
# Usage: ./scripts/start.sh [--build] [--fresh] [--port N]
#   --build   rebuild the jar even if it exists (tests skipped)
#   --fresh   use a throwaway H2 database instead of ./data
#   --port N  listen on port N (default 8080, or $SERVER_PORT)
set -euo pipefail
cd "$(dirname "$0")/.."

PORT="${SERVER_PORT:-8080}"
BUILD=false
FRESH=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --build) BUILD=true ;;
    --fresh) FRESH=true ;;
    --port)  PORT="$2"; shift ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
  shift
done

export BASE="http://localhost:$PORT"
source scripts/lib.sh

JAR=target/courier-tracking-1.0.0.jar
PID_FILE=target/app.pid
LOG_FILE=target/app.log

if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  echo "App already running (pid $(cat "$PID_FILE")). Stop it with ./scripts/stop.sh"
  exit 1
fi
if curl -s -o /dev/null "$BASE" 2>/dev/null; then
  echo "Port $PORT is already in use. Try: $0 --port 8089"
  exit 1
fi

if [[ "$BUILD" == true || ! -f "$JAR" ]]; then
  echo "Building jar..."
  ./mvnw -q -DskipTests package
fi

JAVA_ARGS=(-jar "$JAR" --server.port="$PORT")
if [[ "$FRESH" == true ]]; then
  DB_DIR=$(mktemp -d "${TMPDIR:-/tmp}/courier-tracking-db.XXXXXX")
  JAVA_ARGS+=(--spring.datasource.url="jdbc:h2:file:$DB_DIR/db;LOCK_TIMEOUT=5000")
  echo "Using fresh database in $DB_DIR"
fi

mkdir -p target
nohup java "${JAVA_ARGS[@]}" >"$LOG_FILE" 2>&1 &
echo $! >"$PID_FILE"
echo "Starting app on port $PORT (pid $!, log: $LOG_FILE)..."

if wait_for_health 90; then
  echo "${GREEN}App is up at $BASE${RESET}  (Swagger: $BASE/swagger-ui.html)"
else
  echo "${RED}App did not become healthy in 90 s. Last log lines:${RESET}"
  tail -n 30 "$LOG_FILE"
  kill "$(cat "$PID_FILE")" 2>/dev/null || true
  rm -f "$PID_FILE"
  exit 1
fi
