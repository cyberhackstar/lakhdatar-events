# v2.0.43 — DAST and load qualification corrections

- Fixed k6 runtime crashes by removing `new URL()` from every checkout load script. The k6 stdin runtime does not guarantee the browser/Node `URL` global.
- Corrected the idempotency load probe to reuse the exact same customer payload with its key, use four concurrent retries, and remain below the backend's minimum per-email limiter.
- Kept the success-path checkout probe below the default 20/minute client limiter; the dedicated hot-sale workload treats the explicitly designed 409/429/503 responses as expected outcomes while still checking latency and unexpected transport/server failures. HTTP 503 responses retain a dedicated `rate<0.02` threshold so a broadly unavailable service cannot pass as healthy.
- Corrected the production-target guard to match `events.neelastack.com` and its dot-delimited subdomains; `staging-events.neelastack.com` no longer gets mistaken for production.
- Kept the public catalogue scenario public-only and retained protected operations as dedicated fixture-backed tests.
- Replaced broad inline style-element permission with a per-request CSP nonce in `style-src`. The edge overwrites `X-CSP-Nonce`, nonces SSR `<style>` tags, and publishes the same nonce on `<app-root ngCspNonce>` for Angular's later client-side style insertion. `style-src-attr 'unsafe-inline'` remains narrowly explicit for current style-attribute bindings used by dynamic event branding and logo sizing.
- Mirrored the CSP nonce behavior in the HA edge reference while retaining the v2.0.40 single-owner HSTS directives and both catalogue-location exceptions.
- Added CI regression tests for the k6 hostname guard and tightened security source contracts.
- Did not change backend business logic, payment provider flows, E2E fixture provisioning, database migrations, public route URLs, or the load-test thresholds.

## Validation boundary

This is a qualification-fix candidate. The source guards, shell checks, NGINX runtime checks and ZIP integrity are to be verified before release; GitHub-hosted DAST/load and staging/E2E must be rerun on the final commit. Do not promote to production merely because source checks pass.
