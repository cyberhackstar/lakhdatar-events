# Neelastack Events v2.0.18 — Enterprise Scale Hardening

## Phase 1
- Added bounded per-provider payment bulkheads so slow external gateways cannot consume the entire API thread pool.
- Added provider circuit breaking with fast-fail and half-open recovery behavior.
- Preserved provider idempotency and reconciliation as the correctness authority across HA nodes.
- Made payment-provider concurrency and circuit thresholds configurable.
- Bumped active release metadata to 2.0.18.

## Qualification boundary
This release adds source-level protections for provider degradation and high-concurrency checkout. Runtime qualification must still prove the target workload in staging, including 1,000+ concurrent users, checkout contention, provider latency/outage, webhook replay, refunds, HA failover and restore drills.

- Recovery workers now support configurable batch sizes and bounded multi-batch draining to reduce large-event refund backlog.
- Enterprise qualification now includes the dedicated hot-sale checkout scenario.
