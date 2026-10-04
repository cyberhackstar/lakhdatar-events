# v1.9.30 — Enterprise scale & resilience

- Added a dedicated read-only Platform Operations Health console for PostgreSQL, Redis and recovery queues.
- Added dedicated worker-tier switch so API replicas can disable scheduled jobs and separate worker replicas can execute recovery jobs under distributed locks.
- Added cursor APIs for finance refunds and immutable ledger; existing offset APIs remain backward-compatible.
- Hardened HA NGINX reference configuration with the same security headers, rate limits and token-safe ticket routing as the single-node edge.
- Added SSR host allow-list enforcement using NG_ALLOWED_HOSTS.
- Hardened k6 thresholds so expected 409/429/503 business responses do not count as transport failures.
- Added production qualification, load-test, HA and PITR runbook updates.
- Existing test sources remain unchanged; all new tests are additive.

- Added V28 indexes matching the actual created_at/starts_at cursor ordering used by operational APIs.
- Bounded the admin dashboard portfolio to the six most recent events and moved totals to database-side scoped aggregates.
- Added event operations summary API so operational pages no longer preload the complete event portfolio.
