# Validation plan — v2.0.56

## Local checks to run before pushing

```powershell
node tools/verify-platform-baseline.mjs
node tools/verify-frontend-lock.mjs
node infra/certification/test-browser-artifact-contract.mjs
mvn -f backend/pom.xml -B -ntp clean verify
Push-Location .\frontend; npm ci; npm run test:unit; npm run build; npm run audit:runtime; Pop-Location
Push-Location .\e2e; npm ci; npm run selftest; Pop-Location
```

On a POSIX shell, also run `bash infra/loadtest/test-checkout-target-guard.sh`, `bash infra/loadtest/test-checkout-load-diagnostics.sh`, `bash infra/certification/test-private-repo-workflow-compat.sh`, `bash infra/certification/test-staging-deploy-package.sh`, `bash infra/loadtest/test-scenario-rate-limits.sh`, `bash infra/loadtest/test-invariant-expiry-retry.sh`, and `bash infra/smoke/test-hsts-ownership.sh`.

## Staging qualification

1. Merge only after required CI checks pass, including backend, frontend, security, both CodeQL jobs, and Docker image build/scan/signature verification.
2. Deploy the exact merged SHA to `https://staging-events.neelastack.com` using the protected staging workflow.
3. Run mutation-enabled staging browser E2E.
4. Run Enterprise Release Qualification for the same SHA. The load job must pass with unchanged response, server-error, p95/p99, database invariant and cleanup thresholds. Review the bounded `reservation.stale_order_cleanup.completed` log to confirm stale-order work is rate-limited and remains successful.
5. If checkout fails again, capture the new sanitized `[checkout-load] unexpected HTTP status=... apiCode=... durationMs=...` message and staging structured events. Do not log provider response bodies or secrets.

## Status

Not yet run for the v2.0.56 source SHA. Passing local checks, CI or release qualification does not grant production certification. Production remains HOLD until the exact-SHA single-node operational evidence gate is separately reviewed and passed.
