# v2.0.45 — Load-test rate-limit and reservation-invariant fix

- Root cause from the latest load log: NGINX applies `limit_req zone=public_api ...` to `/api/` at 20 requests/second with burst 40. NGINX's default rate-limit status was 503, so a single CI runner sending the hot-sale workload at up to 100 requests/second recorded 72.01% 503 responses before most requests reached Spring Boot or Cashfree.
- Set `limit_req_status 429` and `limit_conn_status 429` in the edge and HA reference server blocks. Rate and burst limits are unchanged; only the response status for deliberate edge throttling changes. Genuine upstream/application 503 responses remain 503 and remain subject to the existing `checkout_unavailable_503 < 2%` threshold.
- Added `checkout_rate_limited_429` metric to the hot-sale k6 script so rate-limited traffic and true service-unavailable responses are visible separately. Existing request-contract and unavailable thresholds remain in force.
- The post-load invariant check previously ran immediately after the test and could race the 15-second reservation-expiry worker: the log showed only two expired HELD reservations remaining at the instant of the query. The verifier now retries only that exact transient invariant for at most 15 attempts at 2 seconds each; all other invariant violations still fail immediately, and the condition still fails if it persists after the bounded wait.
- Active runtime/build versions aligned to 2.0.45. No payment-provider request, business checkout logic, inventory calculation, DB schema, CSP, HSTS, secrets, or fixture provisioning was changed.

## Validation boundary

Static/regression checks and NGINX configuration syntax are required on this archive. The live Cashfree sandbox, real Cloudflare staging edge, scheduled reservation worker and GitHub Actions workflow are not simulated by local source tests; the final staging load gate must be rerun against the resulting commit SHA.
