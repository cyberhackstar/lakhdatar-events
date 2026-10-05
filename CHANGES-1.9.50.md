# v1.9.50 — Full API Flow + Production Observability Hardening

## API / payment reliability
- Added durable `payment_attempts` history so every provider transaction reference can be mapped back to its local order.
- Cashfree failed, user-dropped and pending transaction IDs are now persisted during server verification/webhook processing.
- Ticket recovery accepts the authoritative Neelastack order number or any previously observed provider payment/transaction ID, while still requiring the original checkout email.
- Successful payment IDs remain authoritative on the main payment record; attempts are never allowed to overwrite a completed payment with a later failed reference.

## Payment-flow hardening
- Cashfree reconciliation now selects a provider payment deterministically and rejects ambiguous multiple-capture states instead of silently choosing the first result.
- Provider transaction history is collision-safe; a transaction ID already bound to another order is treated as a conflict.
- Recovery only issues ticket access tokens for confirmed orders.

## Frontend correctness
- Event public pages show the actual configured payment provider.
- Event-list Publish is disabled for invalid lifecycle/past-start states while the server readiness gate remains authoritative.

## Observability
- Enabled Tempo span-metrics and service-graphs metrics generation with Prometheus remote-write.
- Enabled Prometheus remote-write receiver for the Tempo metrics-generator.
- Replaced the invalid cAdvisor restart-counter alert with restart detection based on `container_start_time_seconds`.
- Split public synthetic monitoring into critical website and public-API SLIs.
- Added alerting for business-metric refresh errors.
- Explicitly configured Spring Boot OTLP tracing export to the private Tempo collector.

## Publish flow
- Kept publish server-authoritative and idempotent.
- Added explicit UI regression checks for publish readiness, lifecycle authorization, and retry-safe publishing.

## Validation
- Static API route inventory and frontend API client coverage reviewed.
- Monitoring YAML/JSON validated.
- Release/version references synchronized to v1.9.50.
