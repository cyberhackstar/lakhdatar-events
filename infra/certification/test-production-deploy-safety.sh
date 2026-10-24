#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
fail() { echo "FAIL: $*" >&2; exit 1; }
PROD='.github/workflows/production.yml'
QUAL='.github/workflows/enterprise-release-qualification.yml'
STAGING='.github/workflows/staging.yml'
DEPLOY='infra/deploy/deploy.sh'
ROLLBACK='infra/deploy/rollback.sh'

# Runner-side SSH material must never be included in the transferred release archive.
grep -Fq 'git archive --format=tar "$RELEASE_SHA"' "$PROD" || fail 'production deploy must archive only the exact release SHA'
grep -Fq 'Release commit tracks a runtime .env file; refusing to transfer it to production.' "$PROD" || fail 'production archive must refuse tracked runtime dotenv secrets'
grep -Fq 'tar -xzf - -C' "$PROD" || fail 'production VM must extract the immutable release archive'
! grep -Eq 'tar .* -czf - \.' "$PROD" || fail 'production must not tar the Actions workspace (which contains SSH files)'
grep -Fq 'Checkout trusted deployment controls at the workflow commit' "$PROD" || fail 'production must use separately checked-out trusted deployment controls'
grep -Fq 'if: always()' "$PROD" || fail 'production must always run the SSH-material cleanup step'
grep -Fq 'rm -f deploy_key known_hosts' "$PROD" || fail 'production cleanup must remove local SSH material even if setup partially fails'
grep -Fq '.deployment-control/infra/certification/verify_production_readiness_artifact.py' "$PROD" || fail 'production readiness gate must run from trusted controls'

# A migration may start during partial Compose failure; the marker must be set before the command.
python3 - <<'PY'
from pathlib import Path
s=Path('infra/deploy/deploy.sh').read_text()
start=s.index('echo "[4/7] Starting backend')
archive=s.index('mv -f "$ROOT/.deploy-attempted.next" "$ROOT/.deploy-attempted"', start)
stage=s.index('DEPLOY_STAGE=4', start)
backend=s.index('IMAGE_TAG="$TAG" "${COMPOSE[@]}" up -d backend', start)
assert start < archive < stage < backend, 'migration-stage/attempted marker must precede backend compose start'
assert 'rm -f "$ROOT/.deploy-attempted" "$ROOT/.deploy-attempted.next"' in s, 'successful deployment must clear attempt marker'
r=Path('infra/deploy/rollback.sh').read_text()
assert 'if [[ -s "$ATTEMPTED" ]]' in r
assert 'TARGET="$(cat "$CURRENT" 2>/dev/null || true)"' in r, 'post-migration failed release should roll back to last accepted release'
assert 'TARGET="$(cat "$PREVIOUS")"' in r, 'normal rollback should target the previous accepted release'
assert 'An unaccepted deployment attempt is recorded. Run the protected rollback workflow before another deploy.' in s, 'ordinary deploys must refuse unresolved attempts'
assert 'Preserving the original .deploy-attempted marker.' in s, 'failed recovery must preserve the failed release marker'
assert 'DEPLOY_RECOVERY_MODE=true exec' in r, 'rollback must explicitly enable recovery mode'
assert 'if [[ "$RECOVERY_MODE" != "true" ]]; then' in s, 'recovery must not overwrite the failed candidate marker before successful acceptance'
PY

# Privileged deployment/qualification workflows are guarded to main as defense-in-depth.
for workflow in "$QUAL" "$STAGING" \
  '.github/workflows/e2e-staging.yml' \
  '.github/workflows/load-test.yml' \
  '.github/workflows/single-node-production-readiness.yml' \
  '.github/workflows/enterprise-production-certification.yml' \
  '.github/workflows/production-ha.yml' \
  '.github/workflows/production-ha-rollback.yml'; do
  grep -Fq "if: github.ref == 'refs/heads/main'" "$workflow" || fail "$workflow is missing the trusted-main job condition"
done
grep -Fq "inputs.operation == 'deploy' && github.ref == 'refs/heads/main'" "$PROD" || fail 'production deploy job must be main-only'
grep -Fq "inputs.operation == 'rollback' && github.ref == 'refs/heads/main'" "$PROD" || fail 'production rollback job must be main-only'

grep -Fq 'git merge-base --is-ancestor "$RELEASE_SHA" refs/remotes/origin/main' "$QUAL" || fail 'qualification must reject release SHAs outside main history'
grep -Fq 'git merge-base --is-ancestor "$RELEASE_SHA" refs/remotes/origin/main' "$STAGING" || fail 'staging deploy must reject release SHAs outside main history'
grep -Fq 'python3 infra/certification/verify_release_qualification_run.py' '.github/workflows/single-node-production-readiness.yml' || fail 'readiness workflow must verify qualification-run provenance'
grep -Fq "'testHarnessSha': os.environ['QUALIFICATION_HARNESS_SHA']" '.github/workflows/enterprise-production-certification.yml' || fail 'enterprise certification must bind artifact to trusted qualification harness SHA'

# Important runtime configuration mappings must be passed through production and staging Compose.
python3 - <<'PY'
import re
from pathlib import Path
import yaml
app=Path('backend/src/main/resources/application.yml').read_text()
required=set(re.findall(r'\$\{([A-Za-z_][A-Za-z0-9_]*)', app)) - {'spring','HOSTNAME'}
for f in ('infra/docker-compose.prod.yml','infra/docker-compose.staging.yml'):
    model=yaml.safe_load(Path(f).read_text())
    env=model['services']['backend']['environment']
    missing=required-set(env)
    assert not missing, f'{f} misses backend environment mappings: {sorted(missing)}'
    raw=Path(f).read_text()
    section=raw[raw.index('    environment:', raw.index('  backend:')):raw.index('    networks:', raw.index('  backend:'))]
    keys=re.findall(r'^\s{6}([A-Z][A-Z0-9_]*):', section, re.M)
    duplicate=sorted({k for k in keys if keys.count(k)>1})
    assert not duplicate, f'{f} has duplicate environment keys: {duplicate}'
PY

echo 'PASS: deployment archive excludes runner workspace, migration-safe rollback markers are ordered, protected workflows validate main provenance, and Compose passes all app env placeholders.'
