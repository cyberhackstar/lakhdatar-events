# Validation — v2.0.57 production hardening

## Current status and known blockers

This is a production-hardening patch candidate, not an authorized production release. The source came from a ZIP and has no Git metadata. The historical release-qualification run `39984461601` failed the load gate on one checkout transport reset; no fresh full GitHub Actions run or remote staging/deployment was available during this patch session.

The launch-content validator was run against the actual legal config and correctly failed closed because it still contains `TODO(owner)` markers and generic operator/address/grievance-officer values. The business/legal owner must confirm the true registered entity name, full registered business address, monitored support and grievance mailboxes, actual appointed grievance officer, and refund processing policy. Do not replace these with invented values just to make the gate green.

The full JVM/frontend/container build, live Cashfree sandbox test, actual GitHub-hosted workflows, VM deployment, R2 retention/lifecycle configuration, backup restore/PITR drill, monitoring/alert test, and rollback drill remain unverified because they require tools or account access outside this patch environment.

## Local checks included in the package

Run these from the repository root in Windows PowerShell (Python 3 is required for the certification manifest tests):

```powershell
node tools/verify-platform-baseline.mjs
node tools/verify-frontend-lock.mjs
node infra/certification/test-browser-artifact-contract.mjs
python infra/certification/test_verify_production_readiness_artifact.py
```

On a POSIX shell, run all existing platform contract scripts, including: `bash infra/certification/test-verify-single-node-readiness.sh`, `bash infra/certification/test-verify-enterprise-evidence.sh`, `bash infra/certification/test-certification-workflow-separation.sh`, `bash infra/certification/test-staging-deploy-package.sh`, `bash infra/certification/test-private-repo-workflow-compat.sh`, `bash infra/certification/test-production-deploy-safety.sh`, `python infra/certification/test_verify_release_qualification_run.py`, `python infra/certification/test_check_production_content_readiness.py`, the `infra/loadtest/test-*.sh` tests, and the smoke HSTS test. The exact-content checker is intentionally expected to fail against the current TODO-filled legal config until owner values are supplied.

Full CI must also run Maven `clean verify`, frontend `npm ci`, unit/build/runtime audit, E2E self-test, image build/scan/signing and CodeQL. Those checks cannot be represented as passed by this static patch process.

## Required live gates

1. Deploy the exact merge SHA to staging.
2. Run mutation-enabled browser E2E and Enterprise Release Qualification against that same application SHA; investigate/re-run the known checkout transport reset gate if it recurs.
3. Complete the protected single-node readiness workflow with real evidence; its successful manifest is now checked automatically by production deployment, avoiding manual `SINGLE_NODE_CERTIFIED_SHA` maintenance.
4. Complete the protected production GitHub Environment reviewer approval and explicit break-glass acknowledgement.
5. Test production backups/restores and configure actual object-store lifecycle retention (the local backup script does not prune remote R2/S3 objects), payment sandbox/live-mode configuration, authenticated SMTP if enabled, observability targets, smoke tests, rollback procedure and alerts.

Production remains on HOLD until all live gates pass.
