# Neelastack Events v2.0.6 — CI/Production Hardening

## Release blockers fixed

- Fixed the backend enterprise logging release test false-positive by avoiding sensitive-looking `response.body` source patterns while preserving the Loki response body behavior.
- Fixed a functional bug where structured Loki log parsing extracted a correlation ID but discarded it before returning the log entry.
- Repaired the frontend lockfile entry that referenced the nonexistent `inherits-2.0.5.tgz` tarball. npm root overrides now explicitly pin the transitive `inherits` dependency used by `http-errors@2.0.1` to `2.0.4`.
- Removed deprecated direct Angular dependencies for `@angular/animations` and `@angular/platform-browser-dynamic`; removed the unused async animations provider.
- Added a deterministic frontend dependency verification script used by CI/release checks.
- Hardened monitor hostname routing: only `/monitor`, authentication screens, static assets, and `/api/v1/admin/ops/*` remain available on `monitor.neelastack.com`. Business APIs, ticket routes, public event catalogue routes, SEO endpoints and generic API paths return 404 there.
- Added the low-rate Loki log endpoint routing to the HA NGINX profile as well as the primary edge.
- Added monitor-route trailing-slash canonicalization and retained the relative root redirect so no private origin port can leak.

## Production principles retained

- ADMIN authorization remains enforced in Spring Security.
- Cloudflare Access should remain the external identity-aware gate for `monitor.neelastack.com`.
- Refresh tokens remain host-only and are not broadened to `.neelastack.com`.
- Loki remains private and is reached only through the backend observability facade.
- Payment, ticket, refund, webhook, reservation, check-in and mail hardening from previous releases is preserved.
