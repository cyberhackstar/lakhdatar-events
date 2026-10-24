#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PREVIOUS="$ROOT/.deploy-previous"
CURRENT="$ROOT/.deploy-current"
ATTEMPTED="$ROOT/.deploy-attempted"
if [[ -s "$ATTEMPTED" ]]; then
  # The current marker is the latest release that completed deployment + internal smoke.
  # A failed post-migration candidate is tracked separately in .deploy-attempted and must
  # not cause rollback to skip the latest known-good release.
  TARGET="$(cat "$CURRENT" 2>/dev/null || true)"
  [[ "$TARGET" =~ ^[0-9a-f]{40}$ ]] || {
    echo "A deployment attempt failed after application startup, but no previously accepted release is recorded. Manual recovery is required; refusing to guess a rollback target." >&2
    exit 2
  }
  echo "Detected an unaccepted in-progress/failed deployment. Restoring last accepted release $TARGET; the database schema is not automatically rolled back." >&2
else
  [[ -s "$PREVIOUS" ]] || { echo "No previous release recorded." >&2; exit 1; }
  TARGET="$(cat "$PREVIOUS")"
  [[ "$TARGET" =~ ^[0-9a-f]{40}$ ]] || { echo "Previous release marker is invalid; refusing rollback." >&2; exit 2; }
  echo "Rolling back to previous accepted release $TARGET; the database schema is not automatically rolled back." >&2
fi
if [[ -s "$ATTEMPTED" ]]; then
  # Recover a failed deployment to the last accepted release.
  DEPLOY_RECOVERY_MODE=true exec "$ROOT/infra/deploy/deploy.sh" "$TARGET"
else
  # Normal rollback to the previously accepted release.
  exec "$ROOT/infra/deploy/deploy.sh" "$TARGET"
fi
