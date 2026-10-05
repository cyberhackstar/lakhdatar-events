# Neelastack Events — service-level objectives

These are engineering targets to validate in staging and then approve as production SLOs; they are not a claim about measured production performance until the qualification report records actual results.

| Signal | Initial target | Alert direction |
|---|---:|---|
| Public/API availability | >= 99.9% | sustained burn |
| API p95 | < 750 ms for normal reads | sustained |
| Checkout API p95 | < 1.5 s excluding provider redirect | sustained |
| Ticket retrieval p95 | < 750 ms | sustained |
| Check-in p95 | < 750 ms | sustained |
| Refund recovery backlog | 0 critical/manual-review surprises | immediate |
| Backup freshness | within approved RPO | immediate |
| Restore objective | RTO <= 30 min target | drill |

Production approval should record actual measured values, traffic profile, topology, and the agreed customer-facing SLA separately from these engineering targets.
