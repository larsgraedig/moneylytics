#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
DUMP_DIR="$ROOT_DIR/db-dumps"

DB_USER=moneylytics
DB_NAME=moneylyticsdb

# shellcheck source=scripts/_lib.sh
source "$SCRIPT_DIR/_lib.sh"

"$SCRIPT_DIR/fetch-dump.sh" "$@"

if [ ! -d "$DUMP_DIR" ]; then
  echo "❌ ERROR: $DUMP_DIR does not exist." >&2
  exit 1
fi

DUMP_FILE=$(ls -1 "$DUMP_DIR"/*.sql.gz 2>/dev/null | sort | tail -n 1) || true
if [ -z "$DUMP_FILE" ]; then
  echo "❌ ERROR: No *.sql.gz files found in $DUMP_DIR" >&2
  exit 1
fi

echo "📦 Importing dump: $(basename "$DUMP_FILE")"

run docker compose -f "$COMPOSE_FILE" up -d

echo "⏳ Waiting for Postgres to be ready..."
n=0
until docker compose -f "$COMPOSE_FILE" exec -e PGPASSWORD=moneylytics postgres \
    psql -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME" -c '\q' >/dev/null 2>&1; do
  n=$((n + 1))
  if [ "$n" -ge 30 ]; then
    echo "❌ ERROR: Could not authenticate against Postgres after 30s." >&2
    exit 1
  fi
  sleep 1
done

ARCHIVE_SCHEMA="public_archived_$(date +%Y%m%d%H%M%S)"

archive_schema() {
  docker compose -f "$COMPOSE_FILE" exec -T -e PGPASSWORD=moneylytics postgres \
    psql -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME" <<SQL
ALTER SCHEMA public RENAME TO "$ARCHIVE_SCHEMA";
CREATE SCHEMA public AUTHORIZATION $DB_USER;
SQL
}

restore_dump() {
  gunzip -c "$DUMP_FILE" | docker compose -f "$COMPOSE_FILE" exec -T -e PGPASSWORD=moneylytics postgres \
    psql -h 127.0.0.1 -U "$DB_USER" -d "$DB_NAME"
}

echo "🗄️  Archiving current public schema to $ARCHIVE_SCHEMA and creating a fresh public schema..."
run archive_schema

echo "♻️  Restoring dump into public schema..."
run restore_dump

echo ""
echo "🎉 Done."
echo "  Imported:          $(basename "$DUMP_FILE")"
echo "  Previous data now: schema \"$ARCHIVE_SCHEMA\" (still queryable, e.g. SET search_path TO $ARCHIVE_SCHEMA;)"
