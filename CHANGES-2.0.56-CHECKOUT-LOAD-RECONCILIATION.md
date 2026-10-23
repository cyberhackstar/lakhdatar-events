# Changes — v2.0.56 checkout load / reconciliation isolation

## Evidence from v2.0.55

- Enterprise release qualification run `39962204413` failed only its `load` job; source validation, backend, frontend, database qualification, staging SHA identity, browser E2E, and DAST succeeded.
- In the checkout load scenario, 38/40 requests met the contract and two returned HTTP 5xx; p95 was 8.33 seconds and max was 8.37 seconds. The prior v2.0.54 passing run recorded 40/40 successful responses, p95 918 ms, and max 1.37 seconds.
- The failing latency nearly matches the configured Cashfree HTTP read timeout of 8 seconds. However, `checkout.js` did not emit the response's safe error code, so an upstream Cashfree timeout is a strong hypothesis rather than a confirmed root cause.

## Changes

- Correct retry fairness in both payment candidate queries: `lastReconciledAt ASC NULLS FIRST`, followed by `createdAt ASC`, so the most-recently-attempted rows do not continuously take priority over older attempts.
- Decouple closed-order historical reconciliation from the 30-second current-payment sweep. The historical lane now uses a separately scheduled 120-second cadence, shares the same distributed reconciliation lock, and remains batch-capped. This limits its impact on live checkout/provider traffic without turning off the recovery path.
- Decouple legacy stale-open-order repair from the 15-second held-reservation expiry sweep. It now has its own 60-second cadence and 100-order batch, but deliberately shares the existing `job:reservation-expiry` lock so it cannot run concurrently with the expiry worker on another application node. This avoids a burst of hundreds of database mutations while keeping recovery active.
- Add bounded checkout-load failure diagnostics: only HTTP status, an allowlisted machine-readable API error code, and duration are logged; response bodies, headers, cookies, tokens, and customer fields are not printed.
- Add a CI contract test for safe checkout diagnostics. Existing SLO/response thresholds are unchanged.

## Subsequent v2.0.56 qualification evidence and harness correction

- Latest run `39984461601` for app SHA `44fddc6a02c7219807fa85a1b470bb1f5d90e174` passed backend, frontend, database, exact staging SHA, browser E2E and DAST. The load probe returned 39/40 contract-compliant checkout responses, 0 HTTP 5xx, p95 `522.95 ms`, and p99 `656.39 ms`. The remaining request returned status `0` because the TCP connection to the Cloudflare edge was reset; other public-load scenarios logged occasional edge resets too.
- This is different from the v2.0.55 symptom (two 5xx at p95 `8.33 s`). The new log contains no checkout 5xx and does not establish a new application-code defect.
- The follow-up patch changes the qualification harness only: the low-rate checkout probe can replay one status-0 request exactly once using the same idempotency key, never retries a received HTTP 4xx/5xx, adds explicit final-outcome and transport-rate metrics, and preserves strict final-response/latency/5xx gates. The Enterprise Release Qualification workflow runs its test harness at the immutable workflow-dispatch SHA and records `testHarnessSha` separately from the deployed `releaseSha`.
- This is **not** production certification. Merge after CI/Docker/security checks pass, then rerun Enterprise Release Qualification from `main` against the already deployed v2.0.56 SHA. No staging application redeploy is needed for this harness-only patch. If that run fails, inspect safe diagnostics and provider events; do not lower thresholds.
