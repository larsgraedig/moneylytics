#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<EOF
Usage: $(basename "$0") <path/to/file.csv>

Import a transaction CSV file into Moneylytics.
The format is detected automatically from the file's header row.

Supported formats:
  MLP Banking  – columns: Kategorie, Unterkategorie, Buchungstag, Valutadatum, Betrag, EUR
  Standard     – columns: Main category, Second Category, booking_date, valuta_date, amount, currency

Options:
  BASE_URL  Override the API base URL (default: http://localhost:8080)
            Example: BASE_URL=https://api.example.com $(basename "$0") transactions.csv

Examples:
  $(basename "$0") web/src/main/resources/AccountSheet.csv
  $(basename "$0") web/src/main/resources/AccountSheet2.csv
  BASE_URL=http://localhost:9090 $(basename "$0") my-export.csv
EOF
}

if [[ $# -eq 0 ]]; then
  echo "Error: no CSV file specified." >&2
  echo >&2
  usage
  exit 1
fi

CSV_FILE="$1"
BASE_URL="${BASE_URL:-http://localhost:8080}"

if [[ ! -f "$CSV_FILE" ]]; then
  echo "Error: file not found: $CSV_FILE" >&2
  exit 1
fi

echo "Uploading $CSV_FILE to $BASE_URL/transactions/import ..."

HTTP_STATUS=$(curl --silent --output /tmp/import_response.json --write-out "%{http_code}" \
  -X POST "$BASE_URL/transactions/import" \
  -F "file=@$CSV_FILE;type=text/csv")

echo "HTTP $HTTP_STATUS"
python3 -m json.tool /tmp/import_response.json 2>/dev/null || cat /tmp/import_response.json
echo

if [[ "$HTTP_STATUS" == "200" ]]; then
  exit 0
else
  exit 1
fi
