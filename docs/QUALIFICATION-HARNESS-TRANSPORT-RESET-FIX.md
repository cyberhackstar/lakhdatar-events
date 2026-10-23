# Qualification harness fix — 23 October 2026

## Why this is separate from the application release

The latest v2.0.56 Enterprise Release Qualification run (application SHA `44fddc6a02c7219807fa85a1b470bb1f5d90e174`) passed source validation, backend, frontend, database, exact-staging-SHA, browser E2E and DAST. The load scenario then received one status-0 response to a checkout POST because the TCP connection to the Cloudflare edge was reset. That one transport-level failure made 39/40 checkout responses contract-compliant (97.5%) and crossed a threshold evaluated over just 40 requests. No checkout 5xx was recorded in this run; checkout p95 was 522.95 ms and p99 was 656.39 ms. Other public read scenarios also logged occasional edge connection resets, below their configured thresholds.

This patch does **not** weaken the final checkout success/error thresholds and does not change application payment logic. The low-rate checkout probe now retries exactly once only when it receives no HTTP response (`status === 0`), reusing the exact request body and idempotency key. It never retries an HTTP 4xx/5xx response. The final logical request still must satisfy the response contract, return no server error, finish with a response, and meet latency thresholds. The test separately caps transport-reset/retry rate below 3%, which permits one recovered transport reset in its 40-request probe but fails if two or more occur.

## Important CI/CD correction

The load job now uses the reviewed qualification harness at the immutable workflow-dispatch SHA (`github.sha`), while the application release remains bound to `inputs.release_sha` and the staging deploy marker. The release qualification manifest records both `releaseSha` and `testHarnessSha`. This lets maintainers fix/re-run the test harness against the already deployed v2.0.56 SHA without rebuilding or redeploying application containers. Other qualification jobs continue to validate the exact application release SHA.

## Operator procedure

1. Merge this test-harness/workflow-only patch after CI, Docker and security checks pass.
2. Do **not** run `staging.yml` for this patch. v2.0.56 remains the deployed application release.
3. Run Enterprise Release Qualification from `main`, using application release SHA `44fddc6a02c7219807fa85a1b470bb1f5d90e174` and `https://staging-events.neelastack.com`.
4. Confirm the artifact records both hashes, the load job is green, and all qualification gates pass. If the retry itself fails or two or more initial no-response errors occur, qualification stays failed. Do not relax thresholds or promote to production on a failed run.

This is a CI qualification improvement, not a production certification. Single-node readiness evidence and the protected production gate remain required.
