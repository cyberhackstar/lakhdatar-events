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

## Validation evidence recorded on 23 October 2026

- Local backend: `mvn -B -ntp clean verify` completed with `BUILD SUCCESS`; 261 tests, 0 failures, 0 errors.
- Local frontend: `npm ci`, unit tests (12 passed), production build, and runtime audit (0 vulnerabilities) passed on the supported Node v24.21.0 toolchain.
- Local E2E provisioning self-test passed. Platform baseline, lock verification, security policy, browser-artifact, readiness, certification, load-test, smoke and shell syntax contracts passed.
- Enterprise Release Qualification run `39984461601` for staged app SHA `44fddc6a02c7219807fa85a1b470bb1f5d90e174`: source validation, backend, frontend, database, staging-release identity, browser E2E and DAST passed. The load job failed on one checkout attempt that returned status `0` after a TCP connection reset by the Cloudflare edge. The checkout scenario ended with 39/40 contract-compliant responses, 0 HTTP 5xx, p95 `522.95 ms`, p99 `656.39 ms`; the one transport failure exceeded the test's rate threshold because only 40 checkout samples were generated.
- This supersedes the v2.0.55 load symptom of two 5xx responses with p95 `8.33 s`; the same 8-second provider-timeout symptom was **not** observed in this v2.0.56 run. The current evidence does not show an application checkout 5xx in the latest run.

## Staging qualification

1. Merge only after required CI checks pass, including backend, frontend, security, both CodeQL jobs, and Docker image build/scan/signature verification.
2. Deploy the exact merged SHA to `https://staging-events.neelastack.com` using the protected staging workflow.
3. Run mutation-enabled staging browser E2E.
4. For the already deployed v2.0.56 application SHA, use the current `main` qualification harness. The load test now permits at most one initial status-0 transport failure in its 40-request probe only when a retry with the same idempotency key succeeds; final checkout contract/server-error/transport/latency and database invariant/cleanup gates remain strict. The manifest records both `releaseSha` and `testHarnessSha`.
5. If the retry fails, a second transport reset occurs, or an HTTP 5xx appears, qualification must fail. Inspect the sanitized `[checkout-load] ... apiCode=... logicalDurationMs=...` message and correlated staging `payment.provider.http.failed` / `payment.provider.http.succeeded` events. Never log provider response bodies or secrets.

## Status

v2.0.56 is deployed to staging but is **not yet enterprise-qualified** because run `39984461601` failed its load gate on one edge TCP reset. This harness-only patch does not require redeploying the application; merge it with CI/security/Docker green, then rerun Enterprise Release Qualification from `main` for the exact staged app SHA `44fddc6a02c7219807fa85a1b470bb1f5d90e174`. Passing qualification still does not grant production certification. Production remains HOLD until the separately protected single-node operational evidence gate is reviewed and passed.
