#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
docker compose up -d postgres redis backend
docker compose up -d web
docker compose up -d edge
for _ in $(seq 1 30); do
  if curl -fsS --max-time 5 http://127.0.0.1:4002/edge-health >/dev/null; then
    break
  fi
  sleep 2
done
curl -fsS --max-time 5 http://127.0.0.1:4002/edge-health >/dev/null
cd e2e
npm ci
npm run install:chromium
export E2E_ENV=local
export E2E_BASE_URL=http://127.0.0.1:4002
npm test -- tests/public-security.spec.js --project=chromium
