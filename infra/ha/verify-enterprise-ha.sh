#!/usr/bin/env bash
set -euo pipefail

: "${PRODUCTION_TOPOLOGY:?PRODUCTION_TOPOLOGY is required}"
: "${HA_NODE_COUNT:?HA_NODE_COUNT is required}"
: "${DB_EXTERNAL_HOST:?DB_EXTERNAL_HOST is required}"
: "${REDIS_EXTERNAL_HOST:?REDIS_EXTERNAL_HOST is required}"

[[ "$PRODUCTION_TOPOLOGY" == "enterprise-ha" ]] || { echo 'Enterprise HA requires PRODUCTION_TOPOLOGY=enterprise-ha' >&2; exit 2; }
[[ "$HA_NODE_COUNT" =~ ^[2-9][0-9]*$ ]] || { echo 'HA_NODE_COUNT must be at least 2' >&2; exit 2; }
[[ "$DB_EXTERNAL_HOST" != localhost && "$DB_EXTERNAL_HOST" != 127.0.0.1 ]] || { echo 'Enterprise HA database must not be localhost' >&2; exit 2; }
[[ "$REDIS_EXTERNAL_HOST" != localhost && "$REDIS_EXTERNAL_HOST" != 127.0.0.1 ]] || { echo 'Enterprise HA Redis must not be localhost' >&2; exit 2; }

if [[ -n "${HA_INGRESS_HEALTH_URL:-}" ]]; then
  [[ "$HA_INGRESS_HEALTH_URL" =~ ^https:// ]] || { echo 'HA_INGRESS_HEALTH_URL must use HTTPS' >&2; exit 2; }
  echo "Configured HA ingress health URL: $HA_INGRESS_HEALTH_URL"
fi

echo 'Enterprise HA topology preflight passed. Actual failover must still be drilled and evidenced.'
