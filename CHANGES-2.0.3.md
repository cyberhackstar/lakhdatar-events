# Neelastack Events v2.0.3 — Production Scale & Monitor Routing

## Fixed

- Replaced the stale frontend `package-lock.json` with the corrected lockfile supplied for the project; invalid `void-elements@2.0.2` and `http-errors@2.0.2` tarball references are removed.
- Canonicalized `monitor.neelastack.com/` at the edge to `https://monitor.neelastack.com/admin/operations`, preventing an internal `:8080` origin port from being carried into the browser URL.
- Added NGINX upstream keepalive pools and a bounded micro-cache for public event catalogue reads to reduce origin/database connection churn during traffic bursts.
- Added edge gzip compression for text/JSON responses, raised the per-client edge connection ceiling to 1,000, and gave cacheable public event reads a dedicated 1,200-RPS per-client budget while retaining the lower generic API rate controls.
- Added Tomcat connection/thread/accept queue tuning for high concurrency with bounded application execution.
- Added Node SSR request/header/keep-alive timeouts for predictable behavior under burst traffic.
- Increased container `nofile` limits in production and HA Compose profiles.
- Disabled Micrometer OTLP metrics push by default because Prometheus already scrapes `/actuator/prometheus`; this removes the recurring failed `localhost:4318` metrics-export attempts observed in production logs.
- Added a staging-only `infra/loadtest/thousands.js` profile for 1,000 concurrent public-read users.

## Scope safety

This release does not change payment state transitions, payment-provider credentials, refunds, reservations, inventory locking, ticket issuance, QR validation, webhook signatures, or public payment APIs.

## Capacity note

The single-node Compose profile is hardened for high concurrency, especially public read bursts. A claim of “thousands of users” still requires an actual load run on the target VM/staging topology. For sustained high write traffic, use the HA profile with multiple API/web replicas and appropriately sized PostgreSQL/Redis capacity.
