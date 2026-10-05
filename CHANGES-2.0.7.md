# Neelastack Events v2.0.8 — CI Release Blocker Fix

## Release blockers fixed

- Corrected `verify-frontend-lock.mjs`: npm root `overrides` are authoritative in `frontend/package.json`; the release verifier now validates the root override plus the resolved `package-lock.json` package entry instead of requiring npm to serialize an `overrides` object into the lockfile root.
- Kept the actual dependency resolution pinned to `inherits` 2.0.4, eliminating the broken `inherits-2.0.5.tgz` registry URL observed by CI.
- Regenerated `frontend/package-lock.json` with npm so the lockfile and `package.json` agree on the release version and dependency tree.
- Preserved the existing removal of deprecated direct Angular animation/dynamic-platform dependencies and the unused animation provider.
- Preserved the dedicated SRE monitor hostname separation and production logging safeguards from v2.0.6.
- Synchronized release metadata to v2.0.8 across backend, frontend, runtime health reporting and production compose defaults.

## CI evidence addressed

The latest CI run reached the frontend dependency gate and failed only because the custom verifier expected a lockfile-root `overrides` object that npm does not need to persist there. The resolved lockfile already contains `inherits` 2.0.4. Gitleaks completed successfully with no leaks detected before the same dependency gate stopped the security job.

## Production principles retained

- Payment verification remains server-authoritative and idempotent.
- Refresh-token cookies remain host-only.
- Loki remains private and is queried only through the admin operations facade.
- Monitor access remains ADMIN-only in the application and should remain protected by Cloudflare Access externally.
- Production deployment remains immutable and fail-closed for existing databases/backups.
