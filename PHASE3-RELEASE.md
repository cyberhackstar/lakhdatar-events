# Lakhdatar Events 1.0.5 — Phase 3 Release

## Reliability and security hardening

- Server-authoritative payment verification using Razorpay order/payment state, amount, currency, and capture state.
- Safe recovery when Razorpay order creation or browser callbacks become ambiguous.
- Idempotent webhook processing with durable persistence and stale-claim recovery.
- Atomic, row-locked ticket check-in to prevent double admission under concurrent scans.
- Inventory upper-bound database constraint to prevent reserved + sold from exceeding configured quantity.
- Deterministic refund reconciliation using Razorpay's documented `X-Refund-Idempotency` request header and refund `receipt` field.
- Provider-side refund lookup support for recovery after ambiguous refund requests.
- Single-flight access-token refresh in the browser.
- HttpOnly SameSite=Strict refresh cookie for browser sessions; access token remains in memory.
- Explicit client logout marker to prevent automatic session resurrection after an interrupted logout.
- Rate-limit Redis/local-fallback hardening.
- Cloudflare-aware client IP handling with literal-IP validation.
- Production configuration guard for HTTPS, secrets, secure auth cookie, and disabled bootstrap.
- Oracle VM deployment/rollback hardening and explicit GHCR authentication in CI/CD.

## Verification performed in this environment

- JSON parsing: passed.
- YAML parsing: passed.
- Bash syntax checks: passed.
- Node/k6 syntax checks: passed.
- Angular TypeScript AST parsing: passed for all application `.ts` files.
- Java parse-level validation: no structural parse errors detected; full dependency-resolved Maven compilation was not available in this environment.
- Source-only refund contract test: passed.
- Production Compose structural contract: passed.
- Credential-storage contract: no refresh/access/ticket/QR credentials found in browser storage.
- ZIP integrity test: performed after packaging.

## Required before live deployment

The release must still pass GitHub Actions dependency-resolved Maven/Angular builds, integration tests, ARM64 Docker builds, and a staging deployment using Razorpay test credentials. A real payment and real Android/iPhone camera-scan rehearsal should be performed before the Dandiya event.
