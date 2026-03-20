#!/usr/bin/env bash
set -euo pipefail

REPO="joe-bor/family-hub-api"
SERVER="root@joe-bor.me"
COMPOSE_DIR="/opt/familyhub"
COMPOSE_FILE="docker-compose.prod.yml"
SERVICE="api"

echo "Fetching latest release version..."
BE_VERSION=$(curl -sf "https://api.github.com/repos/${REPO}/releases/latest" | jq -r '.tag_name // empty' | sed 's/^v//')

if [ -z "$BE_VERSION" ]; then
  echo "No release found — falling back to 'latest'"
  BE_VERSION="latest"
fi

echo "Deploying version: ${BE_VERSION}"

ssh "$SERVER" bash -s "$BE_VERSION" "$COMPOSE_DIR" "$COMPOSE_FILE" "$SERVICE" <<'REMOTE'
set -euo pipefail
BE_VERSION="$1"
COMPOSE_DIR="$2"
COMPOSE_FILE="$3"
SERVICE="$4"

cd "$COMPOSE_DIR"
export BE_IMAGE_TAG="$BE_VERSION"

echo "Pulling images..."
docker compose -f "$COMPOSE_FILE" pull --quiet "$SERVICE"

echo "Starting services..."
docker compose -f "$COMPOSE_FILE" up -d "$SERVICE"

echo "Pruning old images..."
docker image prune -f

echo "Waiting for container to be healthy..."
for i in $(seq 1 15); do
  STATUS=$(docker compose -f "$COMPOSE_FILE" ps "$SERVICE" --format '{{.Health}}' 2>/dev/null || echo "unknown")
  if [ "$STATUS" = "healthy" ]; then
    echo "Service is healthy"
    exit 0
  fi
  sleep 2
done

echo "Warning: service did not become healthy within 30s"
docker compose -f "$COMPOSE_FILE" logs --tail 20 "$SERVICE"
exit 1
REMOTE
