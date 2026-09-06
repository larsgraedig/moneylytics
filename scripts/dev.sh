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

BACKEND_PORT=$(find_free_port 8080)
FRONTEND_PORT=$(find_free_port 5173)

NPM_PID=""

cleanup() {
  [ -n "$NPM_PID" ] && kill "$NPM_PID" 2>/dev/null || true
}
trap cleanup EXIT

docker compose -f "$ROOT_DIR/docker-compose.yml" up -d

echo "Waiting for Postgres to be ready..."
n=0
until docker compose -f "$ROOT_DIR/docker-compose.yml" exec -e PGPASSWORD=moneylytics postgres \
    psql -h 127.0.0.1 -U moneylytics -d moneylyticsdb -c '\q' >/dev/null 2>&1; do
  n=$((n + 1))
  if [ "$n" -ge 30 ]; then
    echo "ERROR: Could not authenticate against Postgres after 30s." >&2
    echo "The volume may have been initialised with different credentials — run 'make reset-db' to wipe and recreate it." >&2
    exit 1
  fi
  sleep 1
done

if [ ! -d "$ROOT_DIR/frontend/node_modules" ]; then
  echo "Installing frontend dependencies..."
  npm --prefix "$ROOT_DIR/frontend" install
fi

echo "  → Frontend: http://localhost:$FRONTEND_PORT  (login: dev@local.dev / local)"
echo "  → Backend:  http://localhost:$BACKEND_PORT"

if [ "$BACKEND_PORT" != "8080" ]; then
  echo "  ⚠ Port 8080 was busy, backend is running on $BACKEND_PORT instead." >&2
  echo "    Google login will NOT work on this port (only http://localhost:8080/login/oauth2/code/google" >&2
  echo "    is registered with Google). Use the local dummy accounts instead (dev@local.dev / local)," >&2
  echo "    or free port 8080 and restart if you need real Google login." >&2
fi

BACKEND_URL="http://localhost:$BACKEND_PORT" \
  npm --prefix "$ROOT_DIR/frontend" run dev -- --port "$FRONTEND_PORT" >/dev/null 2>&1 &
NPM_PID=$!

"$ROOT_DIR/gradlew" -p "$ROOT_DIR" :web:bootRun \
  --args="--spring.profiles.active=local --server.port=$BACKEND_PORT"
