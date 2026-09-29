# Lakhdatar Events — Phase 1 Production Audit

## Scope
Phase 1 focuses on the highest-risk production paths: payment integrity, order recovery, inventory/check-in concurrency, authentication/recovery, event/staff authorization, database constraints, scanner safety, and deployment/backup primitives.

## Critical fixes applied
- Corrected the order-status database constraint to match the actual Java domain states (`CREATED`, `AWAITING_PAYMENT`, `CONFIRMED`, `CANCELLED`, `EXPIRED`).
- Added a follow-up migration that repairs databases where the earlier incorrect constraint may already exist.
- Hardened Razorpay order provisioning so an uncertain provider-order state fails closed instead of creating a second provider order.
- Added provider-side payment fetch/amount/currency/capture validation before ticket fulfillment.
- Added payment-ID consistency checks during captured-payment reconciliation and provider refund reconciliation.
- Kept failed payment attempts retryable instead of prematurely closing the Razorpay order.
- Added payment/order recovery paths for browser disconnects, delayed webhooks, and application restarts.
- Made check-in atomic and server-authoritative, with event/gate/staff authorization and event-window enforcement.
- Ensured rejected/unverified scans are never treated as successful entry.
- Added single-flight frontend token refresh and server refresh-token revocation on logout.
- Added stronger checkout idempotency reuse for safe browser/network retries.
- Hardened asset URLs and CSV export against common production/security issues.
- Added Docker build-context exclusions to avoid leaking local artifacts/secrets and to reduce build context size.

## Static validation performed
- Java/TypeScript brace-balance smoke checks on modified sources.
- Migration/domain enum consistency check for order statuses.
- Shell-script syntax checks where applicable.
- Repository-wide source/config consistency inspection.

## Environment limitation
This audit environment does not have Docker installed, does not have a local Maven installation/cache, and cannot reach the npm registry. Therefore Maven dependency resolution, the full Angular dependency build, Docker image builds, and real Razorpay API calls could not be executed locally. GitHub Actions remains the authoritative CI build/test path.

## Remaining phases
Phase 2 should concentrate on full frontend UX/runtime validation, API contract testing, performance/load testing, stricter proxy trust configuration, observability/privacy hardening, and a complete Oracle VM deployment rehearsal.
