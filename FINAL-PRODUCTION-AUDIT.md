# Lakhdatar Events — Final Enterprise Production Release Audit

Release: **1.0.13**

## Merge source

This release combines:
- the newer 1.0.12 baseline and its security/build hardening; and
- the previously verified 1.0.11 fix for the Windows/OneDrive Maven classpath failure involving `ProviderOrderRecoveryJob.class`.

## Included hardening

- Refresh-token reuse revocation is committed with `@Transactional(noRollbackFor = ApiException.class)`.
- Login performs one BCrypt comparison even when an account is absent, reducing account-enumeration timing differences.
- Razorpay webhook authorization updates are protected by a payment row lock and terminal-state guard.
- Production startup rejects default/short database and application secrets and requires HTTPS production origins/URLs.
- New-ticket and new-ticket-type JPA version fields remain null until persistence so Spring Data selects `persist()` rather than `merge()` for new entities.
- PostgreSQL/Testcontainers integration tests share infrastructure and Surefire test JVM memory is bounded.
- Frontend dependency installation uses the committed `package-lock.json` with `npm ci` in CI and Docker builds.
- ARM64 OCI images and immutable Git-SHA deployment are retained.
- Database backup, health-gated deployment, and image rollback scripts are retained.
- Nginx server fingerprinting is disabled with `server_tokens off`.

## Regression fix in 1.0.13

`ProviderOrderRecoveryJob` is no longer a standalone Spring component. Its scheduled provider-order recovery sweep is hosted directly by `OrderService`, preserving the recovery behavior while eliminating the class boundary that produced the observed missing-class failure during Spring configuration scanning on the Windows/OneDrive workspace.

A dedicated contract test asserts that the scheduler remains on `OrderService` and the standalone class does not return.

## Validation performed in the build sandbox

- Java parser validation: **82 production Java files + 18 test Java files, 0 parse errors**.
- Maven `pom.xml`: XML parse successful.
- Frontend `package.json` / `package-lock.json`: JSON parse successful.
- Spring and Docker Compose YAML: parse successful.
- Deployment/backup shell scripts: `bash -n` successful.
- Release version consistency: `VERSION`, Maven, frontend package manifest, and lockfile all report **1.0.13**.
- No `.env`, backend `target`, frontend `node_modules`, `.class`, or `.jar` build artifacts are included.

## External verification still required before live cutover

A dependency-resolved `mvn -B -ntp clean verify` was not executable in this sandbox because Maven binaries/dependencies could not be downloaded. The repository's 1.0.12 release notes record the frontend production build as verified and the backend sources as syntax-checked; 1.0.13 changes the backend recovery placement plus version/docs/Nginx/deployment metadata.

Before production cutover, run:

```bash
cd backend
mvn -B -ntp clean verify
```

Then deploy the ARM64 images through GitHub Actions or the documented immutable-SHA deployment script and complete the payment/check-in/rollback smoke tests in the production environment.
