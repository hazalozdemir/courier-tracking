#!/usr/bin/env bash
# Stops the app started by ./scripts/start.sh.
set -euo pipefail
cd "$(dirname "$0")/.."

PID_FILE=target/app.pid
if [[ ! -f "$PID_FILE" ]]; then
  echo "No pid file, app not started by start.sh."
  exit 0
fi

PID=$(cat "$PID_FILE")
if kill -0 "$PID" 2>/dev/null; then
  kill "$PID"
  for _ in {1..20}; do kill -0 "$PID" 2>/dev/null || break; sleep 0.5; done
  kill -0 "$PID" 2>/dev/null && kill -9 "$PID"
  echo "Stopped app (pid $PID)."
else
  echo "App (pid $PID) was not running."
fi
rm -f "$PID_FILE"
