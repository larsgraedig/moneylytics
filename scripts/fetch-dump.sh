#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
DUMP_DIR="$ROOT_DIR/db-dumps"

# shellcheck source=scripts/_lib.sh
source "$SCRIPT_DIR/_lib.sh"

: "${DUMP_SSH_HOST:?ERROR: DUMP_SSH_HOST must be set}"
: "${DUMP_SSH_USER:?ERROR: DUMP_SSH_USER must be set}"
: "${DUMP_SSH_REMOTE_DIR:?ERROR: DUMP_SSH_REMOTE_DIR must be set}"
: "${DUMP_SSH_KEY_PATH:?ERROR: DUMP_SSH_KEY_PATH must be set}"
DUMP_SSH_PORT="${DUMP_SSH_PORT:-22}"

if [ ! -f "$DUMP_SSH_KEY_PATH" ]; then
  echo "❌ ERROR: DUMP_SSH_KEY_PATH ($DUMP_SSH_KEY_PATH) does not exist" >&2
  exit 1
fi

SSH_OPTS=(-i "$DUMP_SSH_KEY_PATH" -p "$DUMP_SSH_PORT" -o StrictHostKeyChecking=accept-new -o BatchMode=yes)

mkdir -p "$DUMP_DIR"

fetch_latest_remote() {
  local err_log out status
  err_log="$(mktemp)"
  status=0
  if [ "$VERBOSE" = "1" ]; then
    out=$(ssh "${SSH_OPTS[@]}" "${DUMP_SSH_USER}@${DUMP_SSH_HOST}" \
      "ls -1 ${DUMP_SSH_REMOTE_DIR}/moneylyticsdb_*.sql.gz 2>/dev/null | sort | tail -n 1") || status=$?
  else
    out=$(ssh "${SSH_OPTS[@]}" "${DUMP_SSH_USER}@${DUMP_SSH_HOST}" \
      "ls -1 ${DUMP_SSH_REMOTE_DIR}/moneylyticsdb_*.sql.gz 2>/dev/null | sort | tail -n 1" 2>"$err_log") || status=$?
  fi
  if [ "$status" -ne 0 ]; then
    echo "❌ ERROR: SSH connection to $DUMP_SSH_HOST failed" >&2
    cat "$err_log" >&2
    rm -f "$err_log"
    exit 1
  fi
  rm -f "$err_log"
  echo "$out"
}

echo "🔍 Looking up latest dump on ${DUMP_SSH_USER}@${DUMP_SSH_HOST}:${DUMP_SSH_REMOTE_DIR} ..."
LATEST_REMOTE=$(fetch_latest_remote)

if [ -z "$LATEST_REMOTE" ]; then
  echo "❌ ERROR: No moneylyticsdb_*.sql.gz files found in $DUMP_SSH_REMOTE_DIR on $DUMP_SSH_HOST" >&2
  exit 1
fi

echo "⬇️  Downloading $(basename "$LATEST_REMOTE") ..."
run scp -i "$DUMP_SSH_KEY_PATH" -P "$DUMP_SSH_PORT" -o StrictHostKeyChecking=accept-new -o BatchMode=yes \
  "${DUMP_SSH_USER}@${DUMP_SSH_HOST}:${LATEST_REMOTE}" "$DUMP_DIR/"

echo "✅ Saved to $DUMP_DIR/$(basename "$LATEST_REMOTE")"
