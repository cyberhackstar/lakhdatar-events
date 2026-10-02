#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TAG="${1:-}"
NAMESPACE="${IMAGE_NAMESPACE:-}"
[[ "$TAG" =~ ^[0-9a-f]{40}$ ]] || { echo "Usage: $0 <git-sha>" >&2; exit 2; }
[[ -n "$NAMESPACE" ]] || { echo "IMAGE_NAMESPACE is required" >&2; exit 2; }
mkdir -p "$ROOT/infra/deploy/attestations"
out="$ROOT/infra/deploy/attestations/$TAG.txt"
: > "$out"
for image in lakhdatar-backend lakhdatar-web lakhdatar-edge; do
  ref="$NAMESPACE/$image:$TAG"
  digest="$(docker image inspect --format '{{index .RepoDigests 0}}' "$ref" 2>/dev/null || true)"
  [[ -n "$digest" ]] || { echo "Missing local digest for $ref" >&2; exit 1; }
  echo "$digest" >> "$out"
done
chmod 600 "$out"
echo "Recorded release image digests at $out"
