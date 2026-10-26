#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
node --input-type=module <<'NODE'
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read = (p) => fs.readFileSync(p, 'utf8');
const qualification = read('.github/workflows/enterprise-release-qualification.yml');
const certification = read('.github/workflows/enterprise-production-certification.yml');
const singleNode = read('.github/workflows/single-node-production-readiness.yml');
const evidenceAudit = read('.github/workflows/readiness-evidence-audit.yml');
const production = read('.github/workflows/production.yml');
const haProduction = read('.github/workflows/production-ha.yml');
const validator = read('infra/certification/verify-enterprise-evidence.sh');
const singleNodeValidator = read('infra/certification/verify-single-node-readiness.sh');

// Ordinary qualification must cover application/release gates without requiring external operational attestation.
assert.doesNotMatch(qualification, /^  enterprise-evidence:/m, 'operational evidence gate must live in the separate certification workflow');
assert.match(qualification, /needs: \[backend, frontend, database, staging-release-identity, browser-e2e, load, dast\]/);
assert.match(qualification, /staging-release-identity:[\s\S]*?\.deploy-current/);
assert.match(qualification, /needs: \[backend, frontend, staging-release-identity\]/);
assert.match(qualification, /if: github\.ref == 'refs\/heads\/main'/);
assert.match(qualification, /git merge-base --is-ancestor \"\$RELEASE_SHA\" refs\/remotes\/origin\/main/);
assert.match(qualification, /RELEASE_SHA: \$\{\{ inputs\.release_sha \}\}[\s\S]*?test "\$\(git rev-parse HEAD\)" = "\$RELEASE_SHA"/);
assert.match(qualification, /"manifestType": "release-qualification-not-production-certification"/);
// Application code remains bound to the requested release SHA, while load-test scripts use the
// immutable workflow-dispatch SHA so a harness-only fix can re-qualify an already staged release.
assert.match(qualification, /name: Checkout qualification harness at the workflow commit[\s\S]*?ref: \$\{\{ github\.sha \}\}[\s\S]*?path: qualification-harness/);
assert.match(qualification, /test "\$actual_harness_sha" = "\$EXPECTED_HARNESS_SHA"/);
assert.match(qualification, /"releaseSha": os\.environ\["RELEASE_SHA"\]/);
assert.match(qualification, /"testHarnessSha": os\.environ\["QUALIFICATION_HARNESS_SHA"\]/);
assert.match(qualification, /"productionCertificationGranted": False/);
assert.match(qualification, /name: release-qualification-\$\{\{ inputs\.release_sha \}\}/);
assert.match(qualification, /if-no-files-found: error/);

// Certification is impossible without the matching qualification artifact and protected evidence environment.
assert.match(certification, /if: github\.ref == 'refs\/heads\/main'/);
assert.match(certification, /infra\/certification\/verify_release_qualification_run\.py/);
assert.match(certification, /testHarnessSha': os\.environ\['QUALIFICATION_HARNESS_SHA'\]/);
assert.match(certification, /environment: enterprise-certification/);
assert.match(certification, /actions\/download-artifact@v7/);
assert.match(certification, /run-id: \$\{\{ inputs\.qualification_run_id \}\}/);
assert.match(certification, /releaseSha': os\.environ\['RELEASE_SHA'\]/);
assert.match(certification, /runId': os\.environ\['QUALIFICATION_RUN_ID'\]/);
assert.match(certification, /infra\/certification\/verify-enterprise-evidence\.sh/);
assert.match(certification, /name: enterprise-certification-\$\{\{ inputs\.release_sha \}\}/);

// The production deployment gate must verify a fresh successful readiness artifact for the exact SHA.
assert.match(singleNode, /if: github\.ref == 'refs\/heads\/main'/);
assert.match(singleNode, /verify_release_qualification_run\.py/);
assert.match(singleNode, /environment: single-node-production-readiness/);
assert.match(singleNode, /infra\/certification\/aggregate_readiness_evidence\.py/);
assert.match(singleNode, /R2_ENDPOINT_URL: \$\{\{ vars\.R2_ENDPOINT_URL \}\}/);
assert.match(singleNode, /R2_EVIDENCE_READONLY_ACCESS_KEY_ID/);
assert.match(singleNode, /R2_EVIDENCE_READONLY_SECRET_ACCESS_KEY/);
assert.doesNotMatch(singleNode, /vars\.RPO_SECONDS|vars\.RTO_SECONDS|vars\.SINGLE_NODE_RECOVERY_STATUS/);
assert.match(singleNode, /Upload readiness aggregation diagnostics/);
assert.match(singleNode, /single-node-production-readiness-\$\{\{ inputs\.release_sha \}\}/);
assert.match(evidenceAudit, /schedule:/);
assert.match(evidenceAudit, /aggregate_readiness_evidence\.py --audit-only/);
assert.match(evidenceAudit, /environment: readiness-evidence-audit/);
assert.doesNotMatch(evidenceAudit, /productionReadinessGranted.*True/);
assert.match(singleNodeValidator, /enterpriseHACertificationGranted': False/);
assert.match(singleNodeValidator, /PRODUCTION_TOPOLOGY must be single-node/);
assert.match(singleNodeValidator, /OFFSITE_BACKUP_EVIDENCE_ID/);
assert.match(production, /if: github\.event_name == 'workflow_dispatch' && inputs\.operation == 'deploy' && github\.ref == 'refs\/heads\/main'/);
assert.match(production, /git archive --format=tar \"\$RELEASE_SHA\"/);
assert.doesNotMatch(production, /tar --exclude=.*-czf - \./);
assert.match(production, /infra\/certification\/verify_production_readiness_artifact\.py/);
assert.match(production, /actions: read/);
assert.doesNotMatch(production, /vars\.SINGLE_NODE_CERTIFIED_SHA/);
assert.doesNotMatch(production, /vars\.HA_NODE_COUNT/);
assert.doesNotMatch(production, /vars\.PRODUCTION_TOPOLOGY/);
assert.match(haProduction, /CERTIFIED_RELEASE_SHA: \$\{\{ vars\.ENTERPRISE_CERTIFIED_SHA \}\}/);
assert.match(validator, /attestedByProtectedEnvironment': True/);
assert.match(validator, /Measured RPO/);
assert.match(validator, /Measured RTO/);
console.log('PASS: release qualification remains separate; enterprise HA certification remains fail-closed; production verifies a successful exact-SHA single-node readiness artifact and keeps reviewer/break-glass gates.');
NODE
