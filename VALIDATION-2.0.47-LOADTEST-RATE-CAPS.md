# v2.0.47 validation record

This package addresses the enterprise load run at commit `d06dc2a81d8b20f170588d91e803014f91582aa8`.

## Evidence from the failed run

- All reported stages before ticket PDF passed: catalogue, burst, public event, SEO, checkout idempotency, regular checkout, hot-sale checkout, and scanner check-in.
- Ticket PDF sent about 50 requests/second for two minutes: 5,989 requests, 3,550 HTTP failures (59.27%), and 2,439 successful HTTP 200/PDF responses. This almost exactly matches the edge's `20r/s` plus `burst=40` allowance and therefore points to HTTP 429 rate limiting.
- The 1% `http_req_failed` threshold and PDF 200/content-type checks failed; p95/p99 latency thresholds passed. The job cleaned up the disposable event and accounts and independently passed database invariants.
- A temporary local NGINX harness using the same `20r/s`, `burst=40`, `nodelay`, and `429` settings reproduced the failure signature: at 50 RPS for 10 seconds, 500 requests yielded 240 HTTP 200 and 260 HTTP 429 (52%). At 15 PDF RPS, 75/75 were HTTP 200; at 4 operations iterations/second, 200/200 mixed requests returned HTTP 200. This validates the rate-limit diagnosis and chosen caps locally, not against live staging.
- Operations was not executed because `set -e` stopped the suite after PDF failed. Source review found it inherited the same generic 50 iterations/s, despite four rate-limited requests per iteration; v2.0.47 fixes this latent next failure as well.

## Offline checks for this archive

- `bash infra/loadtest/test-scenario-rate-limits.sh`
- `bash infra/loadtest/test-checkout-target-guard.sh`
- `bash infra/loadtest/test-invariant-expiry-retry.sh`
- `node infra/loadtest/test-refresh-access-token.cjs`
- `bash infra/loadtest/test-loadtest-token-refresh-wiring.sh`
- `bash infra/smoke/test-hsts-ownership.sh`
- platform baseline, frontend lock, and security-policy verification
- shell/JavaScript syntax, YAML parsing, NGINX syntax, package version alignment, and ZIP integrity checks

A successful live v2.0.47 staging run has not yet been performed by this package build. The archive does not claim a post-fix CI/DAST/E2E/enterprise-load pass.

## Existing hot-sale profile interpretation

The same run's hot-sale stage passed its configured overload contract but reported 99.48% HTTP 429. Because the staging test runner presents a single client IP and the shared edge intentionally limits the public API to 20 RPS per client, this result primarily qualifies rate-limit protection, not 100 RPS of checkout business throughput. This patch deliberately does not increase the security limit or reinterpret 429 as successful checkout; testing 100 RPS of actual checkout throughput would require a controlled multi-source load profile and appropriate payment-sandbox safeguards.
