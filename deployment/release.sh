#!/usr/bin/env bash
set -euo pipefail

ENV="${1:-dev}"
case "$ENV" in
  dev)  NAMESPACE="moneylytics-dev" ;;
  prod) NAMESPACE="moneylytics-prod" ;;
  *)    echo "Unknown environment '$ENV'. Use 'dev' or 'prod'."; exit 1 ;;
esac

# 1. Determine current short git commit hash
COMMIT_HASH=$(git rev-parse --short HEAD)
export COMMIT_HASH
echo "Deploying commit: $COMMIT_HASH to namespace: $NAMESPACE"

# 2. Build and push the backend image via Jib
./gradlew :web:jib

# 3. Build and push the frontend image
docker buildx build \
  --platform linux/arm64 \
  --push \
  -t "larsu/moneylytics-frontend:$COMMIT_HASH" \
  ./frontend

# 4. Deploy via Helm, setting image tags to the current commit hash
helm upgrade moneylytics deployment/api \
  --namespace "$NAMESPACE" \
  --set image.tag="$COMMIT_HASH" \
  --set frontend.image.tag="$COMMIT_HASH"
