#!/usr/bin/env bash
set -euo pipefail

: "${PRODUCTION_TOPOLOGY:?PRODUCTION_TOPOLOGY is required}"
: "${HA_NODE_COUNT:?HA_NODE_COUNT is required}"
: "${DB_EXTERNAL_HOST:?DB_EXTERNAL_HOST is required}"
: "${REDIS_EXTERNAL_HOST:?REDIS_EXTERNAL_HOST is required}"

[[ "$PRODUCTION_TOPOLOGY" == "enterprise-ha" ]] || { echo 'Enterprise HA requires PRODUCTION_TOPOLOGY=enterprise-ha' >&2; exit 2; }
[[ "$HA_NODE_COUNT" == 2 ]] || { echo 'Current enterprise HA deployment requires exactly HA_NODE_COUNT=2; add real host provisioning before increasing the count' >&2; exit 2; }
[[ "$DB_EXTERNAL_HOST" != localhost && "$DB_EXTERNAL_HOST" != 127.0.0.1 ]] || { echo 'Enterprise HA database must not be localhost' >&2; exit 2; }
[[ "$REDIS_EXTERNAL_HOST" != localhost && "$REDIS_EXTERNAL_HOST" != 127.0.0.1 ]] || { echo 'Enterprise HA Redis must not be localhost' >&2; exit 2; }

if [[ -n "${HA_INGRESS_HEALTH_URL:-}" ]]; then
  [[ "$HA_INGRESS_HEALTH_URL" =~ ^https:// ]] || { echo 'HA_INGRESS_HEALTH_URL must use HTTPS' >&2; exit 2; }
  curl_args=(--fail --silent --show-error --location --max-time "${HA_HEALTH_TIMEOUT_SECONDS:-10}" --retry "${HA_HEALTH_RETRIES:-2}" --retry-delay 1)
  health_body="$(curl "${curl_args[@]}" "$HA_INGRESS_HEALTH_URL")" || { echo 'HA ingress health check failed' >&2; exit 1; }
  printf '%s\n' "$health_body" | grep -Eq '(^|[^A-Za-z])(UP|ok|healthy|ready)([^A-Za-z]|$)' || { echo 'HA ingress health response did not contain an accepted healthy/readiness marker' >&2; exit 1; }
  echo "Live HA ingress health check passed: $HA_INGRESS_HEALTH_URL"
else
  echo 'ERROR: HA_INGRESS_HEALTH_URL is required for live enterprise HA preflight' >&2
  exit 2
fi

[[ "${DB_EXTERNAL_TLS:-true}" == true ]] || { echo 'DB_EXTERNAL_TLS must be true for enterprise HA' >&2; exit 2; }
[[ "${REDIS_EXTERNAL_TLS:-true}" == true ]] || { echo 'REDIS_EXTERNAL_TLS must be true for enterprise HA' >&2; exit 2; }

echo 'Enterprise HA topology preflight passed; live ingress and external encrypted dependencies verified.'
