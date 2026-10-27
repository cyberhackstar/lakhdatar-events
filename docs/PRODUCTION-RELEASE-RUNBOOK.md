# Production release runbook (single-node)

This is the shortest safe path from `main` to production. It uses the existing workflows unchanged;
`scripts/release-train.sh` only chains them and passes the SHA and qualification run ID between them.

## Pipeline

```
merge to main
  -> CI (tests, scans, multi-arch images, Cosign signatures, attestations)      [automatic]
  -> Staging Deploy            (exact CI-green SHA)                              [scripts/release-train.sh]
  -> Enterprise Release Qualification (E2E, load, DAST, DB upgrade)              [scripts/release-train.sh]
  -> Single-Node Production Readiness (9 evidence controls from R2)              [scripts/release-train.sh]
  -> Production (break-glass confirmation + `production` environment approval)   [you approve]
```

Everything is bound to one exact 40-character SHA. Do not merge to `main` between the Staging Deploy and
Production steps of the same release, or the SHA you qualified is no longer the one you deploy (you would
simply start the train again for the new SHA).

## One-time prerequisites

| Item | Where | Notes |
|---|---|---|
| `DEPLOY_HOST`, `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS` | repository secrets | `DEPLOY_HOST` must include the user, e.g. `ubuntu@203.0.113.10` (production uses it verbatim). |
| `STAGING_DEPLOY_*`, `STAGING_ENV_FILE`, `E2E_STAGING_PROVISIONING_CONFIG`, `MAILSLURP_API_KEY` | `staging` environment | See `STAGING-QUALIFICATION-SETUP.md`. |
| `R2_ENDPOINT_URL`, `R2_EVIDENCE_BUCKET` (variables); `R2_EVIDENCE_READONLY_ACCESS_KEY_ID`, `R2_EVIDENCE_READONLY_SECRET_ACCESS_KEY` (secrets) | `single-node-production-readiness` and `readiness-evidence-audit` environments | Read-only, bucket-scoped token. |
| Production VM `.env` with `ALLOW_SINGLE_NODE_PRODUCTION=true` | `/home/ubuntu/apps/lakhdatar-events/.env` | `deploy.sh` refuses to run without it. |
| `gh` CLI logged in as the repository owner, plus `jq` | your machine | Run the script as yourself so the deploy workflows see a normal `github.actor`. |

## Step 0 - see what is missing (read-only)

Dispatch **Readiness Evidence Audit** (Actions tab, or `gh workflow run readiness-evidence-audit.yml`). It never
grants anything. On failure each blocker is shown as a run annotation (`Readiness blocker: ...`) and in the
job summary, so you can see exactly which of the nine controls is missing, stale, or lacks an independent reviewer.

## The nine evidence controls

The readiness gate is fail-closed by design. Results must be PASS, newer than the age limit, bound to the
release SHA where required, and carry a named operator and (where listed) a different independent reviewer.

| Control | Max age | Release-SHA bound | Produced by | Who has to act |
|---|---|---|---|---|
| `SINGLE_NODE_RECOVERY` | 90 d | yes | real recovery drill + `record-reviewed-evidence.sh` | operator + independent reviewer |
| `OFFSITE_BACKUP` | 30 d | no | `offsite-backup-review.sh` | operator (checks Cloudflare dashboard) |
| `PITR` | 7 d | no | `record-reviewed-evidence.sh` | operator |
| `PEN_TEST` | 90 d | yes | external assessor report via `security-assessment.sh` (`ASSESSMENT_TYPE=independent`) | **external assessor** + independent reviewer |
| `PAYMENT_CHAOS` | 30 d | yes | `payment-chaos.sh` against staging sandbox | operator |
| `ALERTING` | 30 d | no | `record-reviewed-evidence.sh` | operator |
| `FINANCE_RECONCILIATION` | 30 d | yes | `finance-reconciliation.sh` | operator + independent reviewer |
| `PRIVACY_REVIEW` | 90 d | yes | `privacy-review.sh` | business/legal reviewer |
| `MFA` | 90 d | yes | `mfa-verification.sh` (staging) | operator |

Publish results with `infra/readiness/publish-variables.sh` (read-back verified). The independent pen test,
finance reconciliation, privacy/legal review and recovery review are human sign-offs; no workflow can or should
produce them. The short-lived ones (`PITR` 7 d, `OFFSITE_BACKUP`/`ALERTING`/`PAYMENT_CHAOS`/`FINANCE_RECONCILIATION` 30 d)
are best collected just before the release so they do not expire while you wait.

## Step 1 - run the train

```bash
# see which SHA will be used, dispatch nothing
scripts/release-train.sh --dry-run

# staging deploy -> qualification -> readiness (stops before production)
scripts/release-train.sh

# production, after readiness passed (you still approve the `production` environment in GitHub)
scripts/release-train.sh --from production --production --confirm SINGLE-NODE-BREAK-GLASS --sha <the same SHA>
```

If a stage fails, the script prints the failing job/steps and the run URL. Fix the cause, then resume with
`--from qualification`, `--from readiness --qualification-run <id>`, etc.

The default SHA is the newest `main` commit whose push CI run fully succeeded (that is the only kind that has
signed images). Documentation-only commits skip CI, so `main` HEAD may legitimately have no images.

## Known failure modes and what they mean

| Symptom | Cause | Fix |
|---|---|---|
| `qualification run provenance mismatch for path`, or `No successful ... readiness artifact ... (inspected 0 eligible runs)` | GitHub reports a run `path` with or without an `@ref` suffix; the verifiers compared inconsistently. | Fixed: both verifiers normalise the path (`verify_*.py`, with regression tests). |
| `READINESS BLOCKED: ...` | Missing/stale/failed evidence. | See the annotation list; re-collect that control. |
| Staging Cashfree payment flow fails after ~15 min | The single Playwright test hit its 900 s timeout inside the browser flow. | Re-run **Staging Cashfree Payment Flow**; the run summary now names the exact phase that stalled. |
| Dependabot actions PR fails `test-certification-workflow-separation.sh` | The test hard-coded `download-artifact@v7`. | Fixed: any major version is accepted. |
| Staging Deploy: `Staging .env missing or permissions are not 0600` | Runtime `.env` not installed. | Re-run with `configure_runtime=true` once. |
| Production: `Missing deployment secrets` / SSH errors | `DEPLOY_*` secrets absent or `DEPLOY_HOST` lacks `ubuntu@`. | Fix the secret values. |

## Rollback

Dispatch **Production** with `operation=rollback`. Automatic rollback after Flyway starts is intentionally
disabled; the workflow restores the last accepted `.deploy-current` image.
