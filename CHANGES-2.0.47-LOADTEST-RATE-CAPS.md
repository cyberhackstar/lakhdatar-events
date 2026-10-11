# v2.0.47 — Scenario-specific load-test rate caps

## Root cause from the v2.0.46 live run

The ticket PDF profile inherited the generic `RATE=50` from `run-suite.sh`, despite the origin edge applying a shared per-client `20r/s` limit with `burst=40`. Over the 120-second run this admits about 2,440 requests: the log recorded 2,439 successful PDF downloads and 3,550 rate-limited failures. This is the exact pattern expected from deliberate HTTP 429 throttling rather than a PDF service failure.

The not-yet-reached `operations.js` stage inherited the same generic `RATE=50`; each iteration issues five calls, four of which use the same shared limiter. That would likely make operations fail next even after PDF was fixed.

## Changes

- Ticket PDF now uses its own `PDF_RATE`, default 15 RPS and hard-capped at 18 RPS, below the existing 20 RPS limiter. Its 200 status, `application/pdf`, `http_req_failed < 1%`, and latency thresholds are unchanged.
- Operations now uses `OPERATIONS_RATE`, default and hard maximum 4 iterations/second. Four limited requests per iteration yield at most 16 requests/second through the shared limiter, with room for timing variation.
- Both standalone and suite runners pass these variables explicitly; the generic 50 RPS setting is no longer reused by either stage.
- Both staging load workflows expose safe, independently named variables. The enterprise gate fails early for values beyond the limits.
- Added a CI regression test asserting rate isolation, the unchanged edge limits, the existing functional thresholds, and workflow/runner wiring.

## Security and test integrity

No NGINX rate limit was raised, disabled, or bypassed. HTTP 429 is not counted as a successful PDF or operations response. No functional SLO threshold was relaxed. Previous HSTS, CSP nonce, DAST/E2E hardening, just-in-time JWT refresh, checkout 503 measurement, and reservation invariant retry changes are retained.
