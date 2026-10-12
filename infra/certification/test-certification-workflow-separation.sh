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
const production = read('.github/workflows/production.yml');
const haProduction = read('.github/workflows/production-ha.yml');
const validator = read('infra/certification/verify-enterprise-evidence.sh');
const singleNodeValidator = read('infra/certification/verify-single-node-readiness.sh');

// Ordinary qualification must cover application/release gates without requiring external operational attestation.
assert.doesNotMatch(qualification, /^  enterprise-evidence:/m, 'operational evidence gate must live in the separate certification workflow');
assert.match(qualification, /needs: \[backend, frontend, database, browser-e2e, load, dast\]/);
assert.match(qualification, /"manifestType": "release-qualification-not-production-certification"/);
assert.match(qualification, /"productionCertificationGranted": False/);
assert.match(qualification, /name: release-qualification-\$\{\{ inputs\.release_sha \}\}/);
assert.match(qualification, /if-no-files-found: error/);

// Certification is impossible without the matching qualification artifact and protected evidence environment.
assert.match(certification, /environment: enterprise-certification/);
assert.match(certification, /actions\/download-artifact@v7/);
assert.match(certification, /run-id: \$\{\{ inputs\.qualification_run_id \}\}/);
assert.match(certification, /releaseSha': os\.environ\['RELEASE_SHA'\]/);
assert.match(certification, /runId': os\.environ\['QUALIFICATION_RUN_ID'\]/);
assert.match(certification, /infra\/certification\/verify-enterprise-evidence\.sh/);
assert.match(certification, /name: enterprise-certification-\$\{\{ inputs\.release_sha \}\}/);

// The production deployment gate must continue to require the separately protected certified SHA.
assert.match(singleNode, /environment: single-node-production-readiness/);
assert.match(singleNode, /infra\/certification\/verify-single-node-readiness\.sh/);
assert.match(singleNode, /single-node-production-readiness-\$\{\{ inputs\.release_sha \}\}/);
assert.match(singleNodeValidator, /enterpriseHACertificationGranted': False/);
assert.match(singleNodeValidator, /PRODUCTION_TOPOLOGY must be single-node/);
assert.match(singleNodeValidator, /OFFSITE_BACKUP_EVIDENCE_ID/);
assert.match(production, /CERTIFIED_RELEASE_SHA: \$\{\{ vars\.SINGLE_NODE_CERTIFIED_SHA \}\}/);
assert.match(production, /SINGLE_NODE_CERTIFIED_SHA/);
assert.match(production, /NODE_COUNT.*vars\.HA_NODE_COUNT/);
assert.match(production, /TOPOLOGY.*single-node/);
assert.match(haProduction, /CERTIFIED_RELEASE_SHA: \$\{\{ vars\.ENTERPRISE_CERTIFIED_SHA \}\}/);
assert.match(validator, /attestedByProtectedEnvironment': True/);
assert.match(validator, /Measured RPO/);
assert.match(validator, /Measured RTO/);
console.log('PASS: release qualification remains separate; enterprise HA certification remains fail-closed; single-node readiness is a separate attestation and single-node promotion requires its exact reviewed SHA.');
NODE
