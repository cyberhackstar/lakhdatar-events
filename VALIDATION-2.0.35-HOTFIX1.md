# v2.0.35 runtime hotfix 1 validation

## Scope

This hotfix is based on the v2.0.35 production-ready tree and addresses runtime defects discovered during a clean Docker qualification run.

## Observed before hotfix

- PostgreSQL authentication was corrected by recreating the disposable local volume.
- All 41 Flyway migrations applied successfully.
- Backend reached `application.ready` and became healthy.
- Scheduled maintenance jobs then raised `No active transaction for update or delete query` for MFA/password-reset/event-notification cleanup.
- The local Compose stack also attempted OTLP export to `tempo`, which is intentionally absent from the local topology.

## Hotfix

- Declared all JPA modifying repository methods transactionally.
- Added a regression contract test for scheduled mutation transactions.
- Disabled local OTLP trace export by default.

## Verification to run on the developer host

```powershell
cd backend
mvn -B -ntp clean verify -Punit

cd ..
docker compose down -v
docker compose up -d --build
docker compose ps
```

Then allow the application to run long enough for scheduled maintenance sweeps and verify that `docker compose logs backend` contains no `TransactionRequiredException` / `No active transaction` errors.
