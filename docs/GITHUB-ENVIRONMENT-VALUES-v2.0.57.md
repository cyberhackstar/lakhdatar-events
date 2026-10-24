# GitHub Environment values — v2.0.57 deployment model

This is the current configuration guide for the v2.0.57 patch candidate. It is deliberately short on variables: application runtime secrets stay on the protected VM `.env`; the normal deployment environment does not need the entire application `.env` copied from GitHub. Do not put credentials in commits, issue comments, chat, workflow output, or evidence manifests.

## `staging` environment

Required secrets for a normal staging deployment:

| Name | Why required |
|---|---|
| `STAGING_DEPLOY_HOST` | Staging VM hostname/IP, optionally prefixed with `ubuntu@` |
| `STAGING_DEPLOY_SSH_KEY` | Dedicated private deployment key |
| `STAGING_DEPLOY_KNOWN_HOSTS` | Independently verified pinned SSH host-key lines |
| `E2E_STAGING_PROVISIONING_CONFIG` | Required by mutation-enabled E2E/enterprise qualification; least-privilege staging admin email/password/original Base32 TOTP seed and sandbox context only |

`STAGING_ENV_FILE` is optional for normal deployments. Keep it as a protected secret only if you need to deliberately update the staging VM's runtime configuration. In `.github/workflows/staging.yml`, set `configure_runtime=true` only when rotating or changing that config; otherwise the workflow reuses `/home/ubuntu/apps/lakhdatar-events-staging/.env`, checks that it exists with mode `0600`, and the remote deployment script validates the environment before touching containers.

Do not confuse `E2E_ADMIN_TOTP_SECRET` (the original Base32 authenticator seed) with `MFA_ENCRYPTION_KEY` (the application's persistent key for encrypting privileged MFA material). They are different secrets for different purposes.

## `production` environment

Required secrets:

| Name | Why required |
|---|---|
| `DEPLOY_HOST` | Production SSH target, for example `ubuntu@your-vm-host` |
| `DEPLOY_SSH_KEY` | Dedicated deployment private key |
| `DEPLOY_KNOWN_HOSTS` | Independently verified pinned SSH host-key lines |

No production topology, node-count, single-node certified-SHA, public-smoke ticket ID/token, smoke PDF, or smoke mode variables are needed by the v2.0.57 single-node deployment workflow. Topology is fixed by the workflow and the deploy script rejects `PRODUCTION_TOPOLOGY=enterprise-ha`. The workflow also refuses to package tracked runtime `.env` files, transfers only the exact Git archive, and always attempts to clean temporary runner SSH files. If `.deploy-attempted` is present after a failed migration-sensitive deployment, a normal deploy is blocked until the protected rollback workflow is run; if that recovery fails, the marker is preserved. The workflow finds a successful single-node readiness artifact from `main` that matches the release SHA, validates all evidence identifiers and the attestation freshness (90 days), and still requires the protected `production` environment's reviewer approval plus `SINGLE-NODE-BREAK-GLASS` confirmation.

The protected production `.env` on the VM retains the runtime requirements: unique DB/Redis/JWT/ticket/QR/MFA secrets, live Cashfree credentials/webhook settings, real verified support email and a real 8–15 digit public support phone, correct URLs/CORS/host allowlists, and mandatory remote-backup configuration for existing production data. SMTP remains optional by design; if any mail field is configured, the complete settings must validate. Tracing and Loki exports default off unless the separate monitoring stack is provisioned and its endpoints are deliberately enabled.

## `single-node-production-readiness` environment

Topology and node count are hard-coded for the single-node readiness workflow. Optional limits default to `MAX_RPO_SECONDS=300` and `MAX_RTO_SECONDS=1800`; measured `RPO_SECONDS` and `RTO_SECONDS`, nine evidence IDs, and nine `PASS` statuses must still be supplied after actual drills/reviews. These evidence fields are intentionally not fabricated or defaulted: they are a production safety gate, not deployment plumbing. Use a separate `enterprise-certification` environment for an actual multi-node HA topology.

## Minimum normal deployment inputs

- **Staging deploy:** one explicit release SHA, plus three SSH secrets; `STAGING_ENV_FILE` is only needed for an intentional runtime configuration update.
- **Production deploy:** one explicit release SHA, the three SSH secrets, protected environment approval, and the single-node break-glass confirmation. Readiness evidence matching is automated from the successful workflow artifact.
- **Stateful E2E / enterprise qualification:** the separate `E2E_STAGING_PROVISIONING_CONFIG` remains required because tests must authenticate with MFA and create safe fixtures. It must never contain the deployment `.env`, DB secret, JWT/ticket/QR signing keys, SMTP credentials, or deployment SSH key.

The launch legal page also requires owner-confirmed legal entity, complete registered address, monitored support/grievance contacts, actual grievance officer and accurate refund policy in `frontend/src/app/features/legal/legal.config.ts`. Enterprise qualification now blocks while these fields retain owner TODOs or generic placeholders. The exact v2.0.57 candidate must pass fresh full CI and staging qualification; static validation alone is not production certification.
