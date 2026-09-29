#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PREVIOUS="$ROOT/.deploy-previous"
[[ -s "$PREVIOUS" ]] || { echo "No previous release recorded." >&2; exit 1; }
exec "$ROOT/infra/deploy/deploy.sh" "$(cat "$PREVIOUS")"
