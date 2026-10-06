# Validation 2.0.17

## Static checks
- Repository baseline verifier
- Frontend dependency/lock verifier
- JSON/YAML/XML parsing
- Java source balance/import checks
- Shell syntax checks
- Node syntax checks
- Archive integrity and release-manifest consistency

## CI gate required
The previous CI failure was a test-compilation defect in `ProductionHardeningV206ContractTest` where `version` was referenced outside its method scope. v2.0.17 removes that class of defect and adds a release contract for the new hardening controls. The uploaded v2.0.15 log shows production compilation succeeded and failure began during test compilation. CI must still execute `mvn -B -ntp clean verify` on the release commit.

## Staging gates required
- Angular production build and frontend unit tests
- Playwright browser qualification with `E2E_ENV=staging`
- Checkout burst/load and idempotency tests
- Check-in concurrency tests
- Cashfree sandbox webhook/signature/retry/refund qualification
- Flyway fresh + upgrade database qualification through V40
- Docker image build, vulnerability scanning and signed-release verification
- Backup/restore/PITR drill
- HA failover/rollback and public actuator isolation smoke tests

No document in this release claims those environment-dependent gates passed locally.

- V40 adds the durable event-change notification outbox for cancellation and buyer-visible event changes.

- Source-level concurrency qualification includes cancellation using the same ticket-type → event lock order as checkout to prevent deadlock.
