# v1.9.24 — Review-driven reliability fixes

Targeted, backward-compatible fixes from the static code review of v1.9.23. No public API contracts changed.

## Fixed
1. **Payment reconciliation sweeps no longer stall on abandoned checkouts** (high).
   - New column `payments.last_reconciled_at` + index (migration **V24**, forward-only, nullable, no backfill needed).
   - `ReconciliationJob` and `OrderService.recoverMissingProviderOrders` now pick candidates inside a look-back window
     (`app.payment.reconciliation-window-hours`, default 168h) and rotate: least-recently-checked first, each payment
     revisited at most once per `app.payment.reconciliation-recheck-ms` (default 60s; recovery sweep
     `app.payment.recovery-recheck-ms`, default 30s).
   - Payment statuses are deliberately **not** changed on expiry, so a customer who paid after the 12-minute hold
     (lost webhook) is still found by the sweep and refunded as before.
2. **Redis outage no longer blocks gate entry** (high). New `RateLimitService.allowFailOpen`; only `CheckInService.scan`
   uses it (falls back to the per-instance local bucket, limit still enforced). Login, checkout, verify, team and every
   other limiter still use `allow` and stay fail-closed. `RATE_LIMIT_FAIL_CLOSED=true` guard unchanged.
3. **A lost refresh response no longer logs the user out everywhere** (high). Re-presenting a just-rotated refresh token
   within `app.security.refresh-reuse-grace-seconds` (default 30) is rejected with 401 but does **not** revoke the other
   sessions. Reuse after the window still revokes all sessions (theft protection unchanged).
7. **Production guard** (medium). A boot without `prod`/`APP_ENV=production` that serves non-local HTTPS origins
   (`CORS_ALLOWED_ORIGINS`) now fails fast if DB password, `JWT_SECRET`, `TICKET_VIEW_SECRET` or `QR_SIGNING_SECRET`
   still hold the `replace-with-…`/`change-me` placeholders. Local dev (http://localhost) and tests are unaffected.

## Added
- `RateLimitFailOpenTest` — behavioural unit test (mocked Redis down) for fail-closed vs fail-open limiters.

## Version
- 1.9.23 → 1.9.24 in `VERSION`, `backend/pom.xml`, `frontend/package.json`, `frontend/package-lock.json`.

## Deploy notes
- V24 runs automatically via Flyway; it only adds a nullable column and an index (fast, online-safe). Take the usual backup first.
- All new settings have defaults; no `.env` change is required.
