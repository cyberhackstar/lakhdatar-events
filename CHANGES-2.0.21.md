# v2.0.21 — Security & Financial Certification Hardening

## Safety-first scope
- Preserves the v2.0.20 API, database schema, payment flow, HA/PITR contracts, and frontend behavior.
- Fixes a concrete Java compilation regression in `PaymentProviderGuard` so all configured bulkhead/circuit settings are actually wired into provider state.
- Adds focused contract tests preventing future removal of those settings.
- No new database migration is introduced.
- No payment lifecycle semantics are changed.

## Certification focus
- Payment provider capacity protection remains bounded per JVM.
- Provider failures continue to fail fast through the circuit breaker.
- Existing webhook durability, refund idempotency, recovery, MFA, and check-in protections are preserved.
