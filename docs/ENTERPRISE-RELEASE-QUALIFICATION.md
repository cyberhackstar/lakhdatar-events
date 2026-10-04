# Enterprise release qualification — v1.9.31

This release is **enterprise-scale ready at the application architecture level**, but “BookMyShow-class” production availability is an infrastructure and operational claim that must be verified in the target environment.

## Application gates

- Maven `clean verify` must pass with the complete integration-test profile.
- Angular production build and browser tests must pass.
- All Flyway migrations V1–V28 must validate on a fresh database and an upgraded production-like database.
- Payment provider contract tests must cover Razorpay and Cashfree.
- Ticket issuance and check-in concurrency tests must pass.
- Public ticket PDF access must reject missing/invalid credentials.

## Query-scale gates

Admin dashboards and operational tables must remain bounded and use cursor pagination. The dashboard portfolio is intentionally recent-only; organization-wide totals must be computed by database-side scoped aggregates.

## Load gates

Use `infra/loadtest/catalog.js` first against staging. Target p95 < 500 ms / p99 < 1000 ms for read traffic under the configured arrival rate. Run checkout only against a dedicated test event with payment provider sandbox/test mode and `ENABLE_CHECKOUT_LOAD=true`. Run check-in with a prepared pool of valid/expired/duplicate QR credentials.

## High availability gates

At least two application VMs, health-checked ingress, external/managed PostgreSQL with failover/PITR, external Redis HA, and at least two independent tunnel/ingress connectors are required before claiming multi-failure-domain HA. The included `infra/ha/docker-compose.ha.example.yml` is a reference profile, not proof that those external services are enabled.

## DR gates

Enable continuous PostgreSQL WAL archival to off-host immutable storage, perform a monthly restore drill, and record measured RPO/RTO. A logical dump alone does not prove PITR.

## Chaos gates

Execute the scenarios in `infra/chaos/payment-provider-resilience.md`: provider timeout, provider 5xx, duplicate webhook, webhook delayed after payment, Redis unavailable, and backend restart during checkout. The required outcome is no duplicate provider order, no duplicate ticket, correct recovery state, and no financial loss.


## Evidence required before a release is certified

Attach the CI run URL/commit, Maven test report, Angular build/test report, Docker image digests, fresh-and-upgrade Flyway validation, k6 summary JSON/output, payment-chaos results, PostgreSQL PITR restore evidence, and HA failover evidence. Passing static checks alone is not sufficient for the operational certification claim.

## Release evidence matrix

| Gate | Required evidence | Fail condition |
|---|---|---|
| Build/test | Maven `clean verify` + Angular production build/tests | compile/test failure |
| API smoke | public catalog, checkout, recovery, ticket, PDF, admin, finance, operations, check-in | unexpected 4xx/5xx or contract mismatch |
| Load | `infra/loadtest/run-suite.sh` plus JSON summaries | server 5xx >= 1%, SLO breach, data-integrity defect |
| Idempotency | `checkout-idempotency.js` with one dedicated key | duplicate provider order/ticket or 5xx |
| Payment chaos | timeout, 5xx, delayed webhook, duplicate webhook, restart | duplicate charge/ticket or lost financial state |
| HA failover | 2 application VMs + ingress health check + managed DB/Redis failover | customer-visible outage beyond RTO or split-brain writes |
| PITR | off-host WAL archive + restore to timestamp | restore failure or RPO breach |
| Security | secret scan, CodeQL, Trivy, authorization tests | secret leak, critical/high blocker or tenant isolation failure |

## Load-test result collection

The k6 suite writes one JSON summary per scenario under `loadtest-results/` when run with `infra/loadtest/run-suite.sh`. Preserve these files with the release CI artifact and record the target VM/DB/Redis sizing, commit SHA, timestamp, scenario settings and test-event identifier. Never attach customer or payment credentials to the artifact.
