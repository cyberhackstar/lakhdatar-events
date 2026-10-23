# Validation plan — v2.0.55

This is a checklist, not a claim that CI or staging has passed for this SHA.

## Local/source verification

1. `cd backend && mvn -B -ntp clean verify` (new terminal-outcome and lifecycle contract tests included).
2. `cd frontend && npm ci && npm run verify:baseline && npm run verify:dependencies && npm test && npm run build`.
3. `cd e2e && npm ci && npm run selftest`.
4. `bash infra/readiness/test-readiness-kit.sh` and repository baseline/certification contract scripts.
5. Secret/dependency scans and build attestations/signatures as performed by `.github/workflows/ci.yml`.

## Staging gates (same exact source SHA)

1. Deploy that SHA using the **Staging Deploy** workflow; never edit app source on the VM.
2. Verify staging release marker and image identity; ensure the staging Cashfree app ID/secret are a matched sandbox pair with `https://sandbox.cashfree.com/pg`.
3. Run **Enterprise Release Qualification** for that SHA and `https://staging-events.neelastack.com`. Preserve backend/frontend/database/identity/browser/load/DAST artifacts.
4. Repeat mutation-enabled browser qualification and confirm Cashfree Create Order works, idempotent retry returns the same logical order, successful payment reconciles, a ticket is issued once, and scanner accepts the generated QR once. The mutation tests are Chromium-only; cross-browser runs may show expected skips.
5. Inspect the bounded stale-order repair counts and payment reconciliation outcomes. Confirm known terminal provider states are closed; do not clear ambiguous/unavailable provider results. Check refund ledger/invariants after any late captured payment. Keep chaos scenarios sandbox-only.

## Single-node operational readiness

1. Verify and retain off-site backup immutability/retention, PITR target recovery and checksum sidecars.
2. Perform all nine Cashfree sandbox payment-chaos scenarios with actual `c1.*`–`c9.*` evidence; use the least-privilege read-only PostgreSQL account for invariant queries.
3. Reconcile period totals against Cashfree settlement/sales/refund reports; separate operator and finance reviewer.
4. Complete an independent scoped penetration assessment; a self-run ZAP scan does not substitute for independent PEN_TEST evidence.
5. Complete privacy/legal review and privileged MFA functional verification, with reviewers distinct from operators where runbooks require it.
6. Agree the exact RPO/RTO scope. The uploaded recovery evidence covers isolated recovery using the existing VM and excludes replacement-VM provisioning and DNS/tunnel cutover. Routine WAL-segment upload timing alone is not a transaction-level RPO measurement.
7. Only after exact-SHA qualification and human review of evidence may the protected Single-Node Production Readiness workflow run. Then the authorized reviewer—not a script—sets `SINGLE_NODE_CERTIFIED_SHA` to the approved exact SHA.

## Current status

- Package integrity + readiness-kit tests + Bash syntax + private-workflow compatibility check: passed in the packaging environment.
- Maven/JUnit and frontend npm/build checks: not run here (Maven unavailable; no online registry or application provider credentials configured).
- CI, staging deploy, E2E against Cashfree sandbox, load, DAST and readiness workflow: must be run in GitHub Actions for the exact candidate SHA.
- Production: **HOLD**.
