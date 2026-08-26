#!/usr/bin/env bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

find_free_port() {
  local port=$1
  while lsof -ti:"$port" >/dev/null 2>&1; do
    port=$((port + 1))
  done
  echo "$port"
}

DEMO_PROJECT="demo-$$"
DEMO_DB_PORT=$(find_free_port 5434)
export DEMO_DB_PORT
BACKEND_PORT=$(find_free_port 8080)
FRONTEND_PORT=$(find_free_port 5173)
BACKEND_LOG=$(mktemp /tmp/demo-backend-$$.XXXXXX.log)
NPM_PID=""

cleanup() {
  [ -n "$NPM_PID" ] && kill "$NPM_PID" 2>/dev/null || true
  docker compose -f "$ROOT_DIR/docker-compose.demo.yml" -p "$DEMO_PROJECT" \
    down -v >/dev/null 2>&1 || true
  rm -f "$BACKEND_LOG"
}
trap cleanup EXIT

docker compose -f "$ROOT_DIR/docker-compose.demo.yml" -p "$DEMO_PROJECT" down -v >/dev/null 2>&1
docker compose -f "$ROOT_DIR/docker-compose.demo.yml" -p "$DEMO_PROJECT" up -d >/dev/null 2>&1

n=0
until docker compose -f "$ROOT_DIR/docker-compose.demo.yml" -p "$DEMO_PROJECT" exec \
    -e PGPASSWORD=moneylytics postgres \
    psql -h 127.0.0.1 -U moneylytics -d moneylyticsdb -c '\q' >/dev/null 2>&1; do
  n=$((n + 1))
  if [ "$n" -ge 30 ]; then
    echo "ERROR: Demo Postgres did not become ready after 30s." >&2
    exit 1
  fi
  sleep 1
done

echo "  → http://localhost:$FRONTEND_PORT  (login: dev@local.dev / local)"

BACKEND_URL="http://localhost:$BACKEND_PORT" \
  npm --prefix "$ROOT_DIR/frontend" run dev -- --port "$FRONTEND_PORT" >/dev/null 2>&1 &
NPM_PID=$!

"$ROOT_DIR/gradlew" -p "$ROOT_DIR" :web:bootRun \
  --args="--spring.profiles.active=demo --server.port=$BACKEND_PORT --spring.datasource.url=jdbc:postgresql://localhost:$DEMO_DB_PORT/moneylyticsdb" \
  >"$BACKEND_LOG" 2>&1
GRADLE_EXIT=$?

# 130 = Ctrl+C (SIGINT), 143 = SIGTERM — beides normaler Shutdown
if [ "$GRADLE_EXIT" -ne 0 ] && [ "$GRADLE_EXIT" -ne 130 ] && [ "$GRADLE_EXIT" -ne 143 ]; then
  echo "--- Backend startup failed (exit $GRADLE_EXIT) ---" >&2
  cat "$BACKEND_LOG" >&2
  exit "$GRADLE_EXIT"
fi
