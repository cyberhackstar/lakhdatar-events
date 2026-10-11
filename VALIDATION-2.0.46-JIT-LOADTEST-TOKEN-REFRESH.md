# v2.0.46 validation record

This ZIP is a source-level remediation based on the failed enterprise load log at commit `03f230767861ebce94b8df75eef8b560cce2d61e`.

## Failure evidence addressed

- The deployed default access-token lifetime is 15 minutes (`JWT_ACCESS_TOKEN`, default `15m`).
- Fixture provisioning happens before the full suite. The scanner scenario starts approximately 26 minutes after provisioning and had 240/240 HTTP failures, 0% server errors, and failed `checkin_contract`.
- The new runner logs indicate that the hot-sale test recorded 0 actual 503s and passed the strict `checkout_unavailable_503 < 2%` threshold. Its 1/33,299 unexpected response is below the existing contract failure threshold; the new work does not relax that threshold, and bounded status/API-code diagnostics are added for future occurrence.
- The same hot-sale run received 429 for 99.48% of requests. These are counted as designed rate-limit outcomes by the current contract, so that is not the workflow’s failure; it also means this run primarily qualified overload protection rather than 100 requests/second reaching the checkout business path.

## Offline checks required for this package

- `node infra/loadtest/test-refresh-access-token.cjs`
- `bash infra/loadtest/test-checkout-target-guard.sh`
- `bash infra/loadtest/test-invariant-expiry-retry.sh`
- `bash infra/loadtest/test-loadtest-token-refresh-wiring.sh`
- `bash infra/smoke/test-hsts-ownership.sh`
- `node tools/verify-platform-baseline.mjs`
- `node tools/verify-frontend-lock.mjs`
- `node tools/verify-security-policy.mjs`
- `bash -n` all shell scripts and `node --check` all changed JavaScript files
- NGINX config syntax validation, ZIP integrity, and package version/lockfile checks

A live staging rerun is still required to demonstrate scanner authentication after this code change; this document does not claim a post-fix GitHub Actions or staging load pass.
