# v2.0.26 Qualification / Staging Fixes

This patch keeps the application release at v2.0.26 and fixes release-pipeline/runtime qualification issues found in the first staging qualification attempt.

## Fixed

- Enterprise release qualification now has immutable pinned defaults for PostgreSQL, Flyway, k6 and OWASP ZAP, while still allowing reviewed GitHub Environment/Repository overrides.
- Enterprise browser qualification explicitly uses Bash inside the Playwright container. This fixes `set -o pipefail` failures caused by `/bin/sh` being the container default shell.
- Standalone staging E2E validation explicitly uses Bash.
- Enterprise and standalone load-test workflows explicitly validate the required staging load-test secrets before invoking the load gate, reporting all missing names together.
- The enterprise load gate now refuses every target except `https://staging-events.neelastack.com`.
- Flyway qualification can run locally or in CI without image variables because it now falls back to immutable pinned images.
- DAST can run locally or in CI without a ZAP secret because it now falls back to an immutable pinned ZAP image.
- k6 runner defaults are immutable digest-pinned rather than mutable tags.
- Added `STAGING-QUALIFICATION-SETUP.md` documenting the staging E2E/load-test credentials and protected certification prerequisites.

## Intentionally still fail-closed

Enterprise certification evidence is not fabricated. Real HA failover, PITR, penetration testing, payment-chaos, alerting, finance-reconciliation, privacy-review and privileged-MFA evidence must be recorded in the protected `enterprise-certification` environment before final certification can pass.
