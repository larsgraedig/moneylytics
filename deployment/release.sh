#!/usr/bin/env bash
set -euo pipefail

# 1. Determine current short git commit hash
COMMIT_HASH=$(git rev-parse --short HEAD)
export COMMIT_HASH
echo "Deploying commit: $COMMIT_HASH"

# 2. Build and push the image
./gradlew :api:jib

# 3. Deploy via Helm, setting image.tag to the current commit hash
helm upgrade moneylytics deployment/api --set image.tag="$COMMIT_HASH"

