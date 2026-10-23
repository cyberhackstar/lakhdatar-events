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

## Release status

This is a candidate fix, not a claim that Cashfree sandbox reliability is repaired. The same-SHA GitHub CI, staging deployment, staging E2E, and enterprise qualification must be rerun. If the next load test fails, use the sanitized API code plus staging application events `payment.provider.http.failed` / `payment.provider.http.succeeded` to distinguish provider timeout, provider error, circuit/bulkhead saturation, and an application-side exception.
