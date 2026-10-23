#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CI="$ROOT/.github/workflows/ci.yml"
STAGING="$ROOT/.github/workflows/staging.yml"
PRODUCTION="$ROOT/.github/workflows/production.yml"
PRODUCTION_HA="$ROOT/.github/workflows/production-ha.yml"
BASELINE="$ROOT/tools/verify-platform-baseline.mjs"
PUBLIC_GUARD="if: github.event.repository.visibility == 'public'"

# GitHub's attestation API is unavailable for user-owned private repositories on the
# current plan. It may be used only when repository visibility is public.
[[ "$(grep -c 'uses: actions/attest@v4' "$CI")" -eq 3 ]]
[[ "$(grep -cF "$PUBLIC_GUARD" "$CI")" -ge 4 ]]
for f in "$STAGING" "$PRODUCTION" "$PRODUCTION_HA"; do
  grep -qF "$PUBLIC_GUARD" "$f"
  grep -q 'Verify GitHub artifact attestations (public repositories only)' "$f"
  grep -q 'gh attestation verify' "$f"
  grep -q 'cosign verify --certificate-identity-regexp' "$f"
  # Cosign must be able to read private GHCR packages before signature verification.
  grep -q 'Authenticate GitHub Actions runner to GHCR' "$f" || grep -q 'Authenticate release runner to GHCR' "$f"
done

# Release images must not be published if any required code/security job failed.
grep -q 'needs: \[backend, frontend, security, codeql\]' "$CI"

# All images keep BuildKit provenance/SBOM and mandatory keyless Cosign signing.
[[ "$(grep -c 'provenance: true' "$CI")" -eq 3 ]]
[[ "$(grep -c 'sbom: true' "$CI")" -eq 3 ]]
grep -q 'cosign sign --yes' "$CI"
grep -q 'cosign verify --certificate-identity-regexp' "$CI"

# CodeQL still analyzes code but doesn't call the unsupported code-scanning upload API.
grep -q 'upload: never' "$CI"
grep -q 'name: Preserve CodeQL SARIF for private-repository review' "$CI"
for f in "$ROOT/infra/backup/backup-postgres.sh" "$ROOT/infra/backup/install-cron.sh" "$ROOT/infra/deploy/deploy.sh"; do
  grep -qF 'https://*.r2.cloudflarestorage.com) BACKUP_S3_SSE="none" ;;' "$f"
done

# Baseline and Java tests must describe the new supported workflow, not require attestations on private repos.
grep -q 'publicAttestationGuard' "$BASELINE"
grep -q 'public repositories only' "$ROOT/backend/src/test/java/com/neelastack/lakhdatar/service/EnterpriseReleaseV212ContractTest.java"
[[ "$(tr -d '\r\n' < "$ROOT/VERSION")" == "2.0.55" ]]
! grep -Rqs '2.0.44' \
  "$ROOT/.env.example" "$ROOT/.env.staging.example" "$ROOT/infra/ha/.env.ha.example" \
  "$ROOT/backend/src/main/resources/application.yml" "$ROOT/infra/docker-compose.prod.yml" \
  "$ROOT/infra/docker-compose.staging.yml" "$ROOT/infra/ha/docker-compose.ha.example.yml"

printf '%s\n' 'PASS: private repository workflows avoid unsupported GitHub security APIs while retaining Cosign signature and BuildKit metadata verification.'
