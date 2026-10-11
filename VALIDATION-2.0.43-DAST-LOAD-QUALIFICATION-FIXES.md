# v2.0.43 validation record

## Targeted failures addressed

- DAST 10055-6 (`style-src unsafe-inline`): now `style-src` uses a per-request nonce. Inline style elements from SSR are rewritten at the edge and Angular receives the nonce on the root element. The narrower `style-src-attr` exception remains for current dynamic style attributes; this is documented, not hidden.
- Enterprise load exit 107: checkout k6 scripts no longer invoke `new URL()` and share the same production-host policy. The production host guard no longer falsely identifies `staging-events.neelastack.com` as a production subdomain. The idempotency probe keeps request identity/payload stable and stays below the configured anti-abuse limits; the steady success-path checkout probe is below the default per-client budget, while the overload probe treats the documented 409/429/503 responses as expected statuses, retains a dedicated 503 rate threshold below 2%, and still fails unexpected transport/status errors.
- The k6 output directory permission handling, fixture database invariant verification, teardown, automatic staging credentials, and the v2.0.40 HSTS single-owner source contracts are preserved.

## Checks to run

- `bash infra/loadtest/test-checkout-target-guard.sh`
- `bash infra/smoke/test-hsts-ownership.sh`
- `node tools/verify-platform-baseline.mjs`
- `node tools/verify-frontend-lock.mjs`
- `node tools/verify-security-policy.mjs`
- All shell syntax checks, workflow YAML parsing, Maven verify and frontend build.
- GitHub Actions `enterprise-release-qualification` DAST/load jobs, staging deploy and E2E on the same final commit SHA.

Live DAST and load qualification have not been claimed as passed by this validation note.
