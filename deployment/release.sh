#!/usr/bin/env bash
set -euo pipefail

ENV="${1:-dev}"
case "$ENV" in
  dev)  NAMESPACE="moneylytics-dev" ;;
  prod) NAMESPACE="moneylytics-prod" ;;
  *)    echo "Unknown environment '$ENV'. Use 'dev' or 'prod'."; exit 1 ;;
esac

COMMIT_HASH=$(git rev-parse --short HEAD)
export COMMIT_HASH
echo "Deploying commit: $COMMIT_HASH to namespace: $NAMESPACE"

./gradlew :web:jib

helm upgrade moneylytics deployment/api \
  --namespace "$NAMESPACE" \
  --set image.tag="$COMMIT_HASH"
