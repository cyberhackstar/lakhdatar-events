# v2.0.40 — Automatic Staging Qualification

## Included

- Reuses the hardened E2E provisioner for stateful k6 scenarios. Event/ticket IDs, admin and scanner credentials, ticket token, QR token, and idempotency key are generated per run and kept in process; they are not stored as GitHub secrets.
- Generates a uniquely named `loadtest-<run-id>` staging event with 50,000 ticket units, runs the requested workload, checks read-only database invariants over the existing pinned staging SSH connection, and always attempts teardown.
- Removes the manual per-fixture `LOADTEST_*` secret and database URL requirement. The only stable configuration needed for stateful load scenarios is the existing `E2E_STAGING_PROVISIONING_CONFIG` plus existing staging SSH secrets used for invariant checks. Optional load tuning uses defaults and may be overridden by GitHub environment variables.
- Keeps deployment `STAGING_ENV_FILE`, database password, and the E2E provisioning config out of the k6 child environment. The SSH private key is passed only to the isolated read-only database verifier.
- Runs post-workload database invariants even when k6 exits with a threshold failure, then attempts cleanup; failure of invariant validation or teardown makes the qualification fail.
- Adds a public query-parameter XSS regression test and private SSR cache-prevention headers.
- Resolves duplicate HSTS at the origin edge: NGINX emits one valid header, hides upstream Spring/SSR copies, and explicitly covers cache locations. The public HTTPS response carries that single origin header through Cloudflare. Narrows CSP image/connect sources and mounts the ZAP policy correctly into the scanner container.
- Adds narrowly documented INFO dispositions for reviewed ZAP heuristics; duplicate HSTS and wildcard/scheme-wide CSP findings remain blocking, and unlisted findings retain the baseline default severity.
- Fixes database invariant SQL variable binding and keeps the earlier payment recovery/Cashfree first-create improvements in the consolidated release.

## Safety and limitations

- Workflows refuse any public target other than `https://staging-events.neelastack.com`; checkout load is explicitly forbidden against production.
- The real backend payment-safety endpoint is checked by the shared provisioner before fixtures are created. The selected provider must actually be in sandbox/test mode.
- Fixture values are masked in GitHub Actions and never written to artifacts or output variables. The load-test environment is process-local.
- Statefully generated test event/account data is disposable and cleanup failure fails the run.
- Real GitHub Actions staging runs are still required to confirm actual database, ZAP, k6 thresholds, browser compatibility, and payment/webhook/reconciliation. Passing mock tests does not certify the live system.

## Staging smoke hotfix (2026-10-11)

Fixed the first HSTS ownership hotfix after live staging showed the public HTTPS response had zero HSTS headers. NGINX edge is now the single HSTS writer; upstream Spring/SSR copies are hidden. The two cache locations explicitly add HSTS because their X-Cache-Status add_header directive overrides server-level add_header inheritance in the pinned NGINX 1.27 image. Direct-origin and public HTTPS smoke checks require exactly one valid HSTS header, with mock-curl regression coverage. Confirm Cloudflare is not separately adding HSTS before production.
