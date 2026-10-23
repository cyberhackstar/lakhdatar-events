# GitHub environment values — v2.0.55 candidate

This guide records safe defaults and required names for the current workflow chain. It does not contain runtime credentials. GitHub Actions secrets cannot be read back into a report from the connected tools; put actual private values directly into the protected GitHub Environment UI, never into a commit, issue, log, or evidence report.

## 1. Staging environment: smallest safe configuration

### Required secrets

| Secret | Purpose |
|---|---|
| `STAGING_DEPLOY_HOST` | Hostname/IP only (workflow adds `ubuntu@` when omitted). Confirm it is the staging host; sharing the VM with production is allowed only if the isolated staging Compose/network/loopback setup is the one currently in use. |
| `STAGING_DEPLOY_SSH_KEY` | Dedicated deployment private key; do not reuse a broad personal SSH key if avoidable. |
| `STAGING_DEPLOY_KNOWN_HOSTS` | Pinned host-key line(s), verified independently. |
| `STAGING_ENV_FILE` | Complete staging-only env file based on `.env.staging.example`, with secrets replaced by real staging values. Keep this as one protected secret; do not split application secrets into extra GitHub secrets. |
| `E2E_STAGING_PROVISIONING_CONFIG` | Separate least-privilege E2E identity configuration used by mutation-enabled browser/qualification tests. Never reuse `STAGING_ENV_FILE` here. |

The package now fixes `.env.staging.example` to use `DB_URL=jdbc:postgresql://postgres:5432/lakhdatar_staging`, keeps `CASHFREE_BASE_URL=https://sandbox.cashfree.com/pg`, and leaves inactive optional Razorpay, Cloudinary, mail, and bootstrap secrets blank. For Cashfree-only testing, fill the required sandbox Cashfree app ID/secret; do not use live credentials. Required random staging secrets include `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`, `TICKET_VIEW_SECRET`, `QR_SIGNING_SECRET`, and `MFA_ENCRYPTION_KEY`. Generate these locally with a cryptographically secure random generator, keep the values only in the protected env secret, and do not paste them into this chat or project files.

### E2E provisioning secret shape

Use the existing protected staging admin, already authorized for the target organizer and enrolled in privileged MFA. The provisioning secret contains only this limited set (no Cashfree/Razorpay API keys, database values, JWT signing keys, SMTP secrets, or deployment key):

```dotenv
APP_ENV=staging
DEFAULT_PAYMENT_PROVIDER=CASHFREE
E2E_ADMIN_EMAIL=<dedicated staging admin email>
E2E_ADMIN_PASSWORD=<dedicated staging admin password>
E2E_ADMIN_TOTP_SECRET=<original base32 TOTP seed, not the 6-digit code>
E2E_ORGANIZER_SLUG=<real organizer slug if staging has multiple organizers>
```

`E2E_ORGANIZER_SLUG` may be omitted only if the staging account can see exactly one organizer. Do not disable MFA to simplify automation. Fixture IDs, bearer tokens, event IDs, ticket IDs, QR tokens and gate values are generated automatically by the provisioner; the old per-test fixture secrets should remain absent.

## 2. Optional staging variables: leave unset when defaults are correct

- `STAGING_PUBLIC_URL` defaults to `https://staging-events.neelastack.com`.
- `POSTGRES_CERTIFICATION_IMAGE`, `FLYWAY_CERTIFICATION_IMAGE`, `K6_IMAGE`, and the ZAP image override default to the workflow's digest-pinned references. Use the matching `vars.*` or `secrets.ZAP_IMAGE` location only when deliberately changing a reviewed image digest.
- Load-profile overrides such as `LOADTEST_THOUSANDS_MAX_VUS`, `LOADTEST_THOUSANDS_RAMP`, `LOADTEST_THOUSANDS_HOLD`, `LOADTEST_CHECKOUT_TARGET_RATE`, `LOADTEST_PDF_RATE`, and `LOADTEST_OPERATIONS_RATE` already have workflow defaults. Do not create them just to make the standard qualification run.
- The v2.0.55 reconciliation batch/window/recheck limits live in `application.yml` defaults. They do not add required GitHub Environment variables.

This leaves the normal staging deployment on four required deployment secrets plus the one separate E2E provisioning secret. Existing real secrets are not introspectable/recoverable through this workflow; re-enter them securely or rotate them if they are lost.

## 3. Exact release flow

1. **Local source**: open the extracted package in Windows VS Code; check `git status`, run the local commands listed in `VALIDATION-2.0.55-RELEASE-QUALIFICATION.md`, commit, and push to a feature branch. Do not develop on the production VM.
2. **CI**: `.github/workflows/ci.yml` must pass for the exact commit; it runs backend/frontend/security/CodeQL contracts and publishes signed images with SBOM/provenance.
3. **Staging Deploy**: manually run `.github/workflows/staging.yml` with that exact `release_sha`. It validates staging host/config, checks provider endpoint/key modes, updates `.env` atomically, creates a backup before deployment, and performs external HTTPS smoke checks.
4. **Enterprise Release Qualification**: manually run `.github/workflows/enterprise-release-qualification.yml` with the same `release_sha` and `https://staging-events.neelastack.com`. It checks the staging release marker, backend, frontend, migration path, stateful E2E, load, and DAST; its manifest explicitly does not grant production certification.
5. **Single-Node Production Readiness**: first complete and review every operational category and its retained evidence; run `.github/workflows/single-node-production-readiness.yml` with the exact same SHA and the successful qualification run ID. This protected gate is separate from release qualification and does not certify multi-node HA.
6. **Production**: an authorized reviewer sets the protected production variable `SINGLE_NODE_CERTIFIED_SHA` to the exact approved SHA only after readiness succeeds. Then run `.github/workflows/production.yml` with its explicit single-node break-glass acknowledgement. Do not set the variable now for v2.0.55.

## 4. Known safe non-secret values

| Environment | Name | Value |
|---|---|---|
| `staging` | `STAGING_PUBLIC_URL` (optional) | `https://staging-events.neelastack.com` |
| `staging` | `CASHFREE_BASE_URL` inside `STAGING_ENV_FILE` | `https://sandbox.cashfree.com/pg` |
| `staging` | `DEFAULT_PAYMENT_PROVIDER` inside `STAGING_ENV_FILE` / E2E config | `CASHFREE` |
| `production` | `PRODUCTION_TOPOLOGY` | `single-node` (confirmed topology in the supplied continuity record; still verify against current actual production deployment) |
| `production` | `HA_NODE_COUNT` | `1` (same note as above) |
| `single-node-production-readiness` | `PRODUCTION_TOPOLOGY` | `single-node` |
| `single-node-production-readiness` | `HA_NODE_COUNT` | `1` |
| all envs | `MAX_RPO_SECONDS` | `300` default; can stay unset |
| all envs | `MAX_RTO_SECONDS` | `1800` default; can stay unset |

## 5. Readiness evidence: values that must NOT be guessed

The readiness workflow requires separate status/evidence pairs for single-node recovery, off-site backup, PITR, penetration testing, payment chaos, alerting, finance reconciliation, privacy/legal review, and privileged MFA. The pair count is intentional: each `PASS` must mean the exercise/review actually completed, and its evidence ID must point to the report retained in R2 with its SHA-256 sidecar verified. Collapsing all evidence to a default or prefilled status would weaken the operational attestation; the app/workflow tuning variables have been reduced instead.

Current findings and candidate keys from the supplied records/diagnostics:

- Off-site review: `production/readiness-evidence/offsite-backup-review-20261023T072911Z.txt` (report and sidecar were byte-verified in R2). Operator/reviewer should still confirm the review scope and retention/access controls before setting status PASS.
- Alerting candidate: `production/readiness-evidence/alerting-final-validation-20261020T004554Z.txt`; this report covers email delivery and simulated faults, not intentional failures of the real production service. Reviewer approval is required.
- PITR/full-recovery candidate: `production/readiness-evidence/recovery-drills/rpo-canary-20261023T011308-10-20261023T015815Z/full-recovery-report-rpo-canary-20261023T011308-10-20261023T015815Z.txt` and the companion report in the same R2 prefix. The isolated recovery passed, but the scope is current-VM recovery with a private Docker network; it excludes replacement-VM provisioning and DNS/Cloudflare Tunnel cutover.
- WAL availability canary: `production/readiness-evidence/rpo-canary-20261023T005204Z/rpo-canary-20261023T005204Z.txt` recorded a 159-second routine-window availability-latency candidate and was independently checksum-verified. **Do not set `RPO_SECONDS=159` from this value.** The evidence explicitly says this is not a transaction-level/whole-disaster RPO certification.
- Successful full isolated RTO runs include 455s / 503s / 93s depending on the exact drill and scope. **Do not choose one for `RTO_SECONDS` without formally accepting the objective/scope.** The supplied records show prior failed elapsed-time attempts and exclude replacement VM/DNS/tunnel recovery. A full node-loss RTO is not established.
- Independent penetration-test evidence, the complete nine-scenario staging Cashfree payment-chaos report, finance/settlement reconciliation, privacy/legal review, and full privileged-MFA evidence were not established by the supplied reports. Do not set their status to PASS just because static checks or a login/TOTP smoke test passed.

Until those categories are reviewed and genuinely passed for the intended recovery scope, production stays **HOLD** and `SINGLE_NODE_CERTIFIED_SHA` remains unset.
