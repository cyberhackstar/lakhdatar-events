# Neelastack Events v2.0.35 — runtime hotfix 1

## Fixes

- Made all declared JPA `@Modifying` repository mutations transaction-safe, including MFA cleanup, password-reset cleanup, event-notification history cleanup and ticket-mail history cleanup.
- Added a release contract test preventing scheduled cleanup transaction regressions.
- Disabled OTLP trace export by default in the local Docker Compose stack because the local profile does not provision Tempo. Production/staging observability settings remain externally configurable.

## Root cause found during local qualification

The backend reached `application.ready`, but scheduled cleanup queries were executing without an active transaction and raised `TransactionRequiredException`. The errors were visible for MFA challenge, password reset and event-notification cleanup paths.
