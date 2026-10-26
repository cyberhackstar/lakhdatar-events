#!/usr/bin/env bash
set -euo pipefail

SCRIPT="infra/readiness/run-payment-chaos-staging.ps1"
[[ -s "$SCRIPT" ]] || { echo "missing staging payment-chaos runner: $SCRIPT" >&2; exit 1; }

require_literal() {
  local needle="$1"
  grep -Fq -- "$needle" "$SCRIPT" || {
    echo "missing required staging safety contract: $needle" >&2
    exit 1
  }
}
reject_literal() {
  local needle="$1"
  if grep -Fq -- "$needle" "$SCRIPT"; then
    echo "forbidden action in staging payment-chaos runner: $needle" >&2
    exit 1
  fi
}

# Fixed-origin, staging-only, exact-release identity.
require_literal "https://staging-events.neelastack.com"
require_literal 'if ($StagingUrl -ne $ExpectedStagingUrl)'
require_literal "/home/ubuntu/apps/lakhdatar-events-staging"
require_literal "ROOT='/home/ubuntu/apps/lakhdatar-events-staging'"
require_literal "staging root guard failed"
require_literal 'APP_ENV staging'
require_literal 'SPRING_PROFILES_ACTIVE staging'
require_literal 'DEFAULT_PAYMENT_PROVIDER CASHFREE'
require_literal 'CASHFREE_BASE_URL https://sandbox.cashfree.com/pg'
require_literal 'CASHFREE_API_VERSION 2025-01-01'
require_literal "DEPLOYED_SHA=$sha"
require_literal "release-qualification-$ReleaseSha"
require_literal "Release qualification result (not production certification)"

# Container and data-plane isolation contracts.
require_literal 'lakhdatar-events-staging'
require_literal 'lakhdatar_pg_data_staging'
require_literal 'lakhdatar_redis_data_staging'
require_literal 'lakhdatar-staging-postgres'
require_literal 'lakhdatar-staging-redis'
require_literal 'lakhdatar-staging-backend'
require_literal 'lakhdatar-staging-web'
require_literal 'lakhdatar-staging-edge'
require_literal 'docker exec lakhdatar-staging-postgres pg_isready'
require_literal 'http://127.0.0.1:4003/edge-health'

# Restarts need exact interactive confirmations and verify post-restart readiness.
require_literal "RESTART STAGING POSTGRES"
require_literal "RESTART STAGING REDIS"
require_literal 'docker restart --time 30 "$CONTAINER"'
require_literal 'STAGING_RESTART=PASS'

# All nine scenarios, separate c10 sandbox-only confirmation, real observation, and evidence gates.
for scenario in c1 c2 c3 c4 c5 c6 c7 c8 c9; do
  require_literal "Id='$scenario'"
done
require_literal "c10=yes:all scenarios completed on staging with sandbox/test payment credentials only"
require_literal 'Observed result: P = expected behavior verified, F = failed, S = skipped'
require_literal 'Operator-reported outcome: $Outcome'
require_literal 'No R2 upload or GitHub Environment variable change was made.'
require_literal 'R2_UPLOAD_READBACK=PASS'
require_literal 'awsx s3api head-object --bucket "$BUCKET" --key "$KEY"'
require_literal 'R2 upload refused: immutable evidence object already exists'
require_literal 'sha256sum -c'

# No route is allowed to mutate production or publish certification variables.
reject_literal 'docker compose down'
reject_literal 'docker volume rm'
reject_literal 'docker system prune'
reject_literal 'gh variable set'
reject_literal 'ENTERPRISE_CERTIFIED_SHA'
reject_literal 'PRODUCTION_DB_HOST='

echo "PASS: staging payment-chaos runner safety contracts."
