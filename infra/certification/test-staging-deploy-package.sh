#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORKFLOW="$ROOT/.github/workflows/staging.yml"
DEPLOY="$ROOT/infra/deploy/deploy-staging.sh"
CI="$ROOT/.github/workflows/ci.yml"

fail() { echo "FAIL: $*" >&2; exit 1; }

[[ -s "$ROOT/VERSION" ]] || fail "repository VERSION file is missing"
grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+$' "$ROOT/VERSION" || fail "VERSION must contain a semantic release version"
STAGING_ENV_EXAMPLE="$ROOT/.env.staging.example"
grep -Fxq 'DB_URL=jdbc:postgresql://postgres:5432/lakhdatar_staging' "$STAGING_ENV_EXAMPLE" || fail "staging example must use the isolated lakhdatar_staging database required by the deployment workflow"
grep -Fxq 'CASHFREE_BASE_URL=https://sandbox.cashfree.com/pg' "$STAGING_ENV_EXAMPLE" || fail "staging Cashfree endpoint must remain sandbox-only"
! grep -Fq 'RAZORPAY_KEY_ID=<REPLACE_WITH_SECRET>' "$STAGING_ENV_EXAMPLE" || fail "inactive Razorpay credentials must be blank in a Cashfree-only staging template"
grep -Fq 'APP_VERSION="$(tr -d' "$DEPLOY" && grep -Fq '< "$ROOT/VERSION")"' "$DEPLOY" || fail "staging deployment must derive APP_VERSION from VERSION"
grep -Fq "VERSION \"\$STAGING_SSH_TARGET:\$remote/VERSION\"" "$WORKFLOW" || fail "staging workflow does not copy VERSION to remote repository root"
grep -Fq "test -s '\$remote/VERSION'" "$WORKFLOW" && grep -Fq "tr -d" "$WORKFLOW" || fail "staging workflow does not normalize and validate VERSION before writing .env"
grep -Fq "tmp='\$remote/.env.next'" "$WORKFLOW" && grep -Fq 'mv -f' "$WORKFLOW" || fail "staging workflow must atomically replace .env rather than truncate the active file"
grep -Fq 'PRODUCTION_TOPOLOGY=enterprise-ha' "$WORKFLOW" || fail "staging workflow does not reject production HA topology in its environment"
grep -Fq "infra/docker-compose.staging.yml \"\$STAGING_SSH_TARGET:\$remote/infra/docker-compose.staging.yml\"" "$WORKFLOW" || fail "staging Compose file is not installed"
grep -Fq "infra/deploy/deploy-staging.sh \"\$STAGING_SSH_TARGET:\$remote/infra/deploy/deploy-staging.sh\"" "$WORKFLOW" || fail "staging deploy script is not installed"
grep -Fq "infra/smoke/staging-smoke.sh \"\$STAGING_SSH_TARGET:\$remote/infra/smoke/staging-smoke.sh\"" "$WORKFLOW" || fail "staging smoke script is not installed"
grep -Fq 'bash infra/certification/test-staging-deploy-package.sh' "$CI" || fail "CI does not run this staging deployment package regression test"

# Ensure release files are copied and validated before the new secret env file
# is atomically installed or the deployment script is invoked.
workflow_body="$(sed -n '/- name: Install staging deployment files/,/- name: External HTTPS staging smoke/p' "$WORKFLOW")"
copy_version_line="$(grep -nF "VERSION \"\$STAGING_SSH_TARGET:\$remote/VERSION\"" <<<"$workflow_body" | head -n1 | cut -d: -f1)"
remote_preflight_line="$(grep -nF "test -s '\$remote/VERSION'" <<<"$workflow_body" | head -n1 | cut -d: -f1)"
env_write_line="$(grep -nF 'mv -f' <<<"$workflow_body" | grep -F '.env' | head -n1 | cut -d: -f1)"
deploy_line="$(grep -nF 'deploy-staging.sh' <<<"$workflow_body" | grep -F 'chmod +x' | head -n1 | cut -d: -f1)"
[[ -n "$copy_version_line" && -n "$remote_preflight_line" && -n "$env_write_line" && -n "$deploy_line" ]] || fail "could not determine the staging install/deploy order"
(( copy_version_line < remote_preflight_line && remote_preflight_line < env_write_line && env_write_line < deploy_line )) || fail "staging release-file preflight must occur before .env replacement and deployment"


backend_start_line="$(grep -nF 'IMAGE_TAG="$TAG" "${COMPOSE[@]}" up -d backend' "$DEPLOY" | head -n1 | cut -d: -f1)"
backend_boundary_line="$(grep -nF 'DEPLOY_STAGE=4' "$DEPLOY" | head -n1 | cut -d: -f1)"
[[ -n "$backend_start_line" && -n "$backend_boundary_line" ]] || fail "backend start/rollback boundary is not explicit"
(( backend_boundary_line < backend_start_line )) || fail "the rollback boundary must move before starting a backend that may migrate the database"

echo "PASS: staging deployment package, atomic environment update, and migration-safe rollback guards are present"
