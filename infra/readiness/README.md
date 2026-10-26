# Single-node production readiness evidence kit

This kit is an **evidence collector**, not an application release or certification bypass. It writes human-readable reports, SHA-256 sidecars, and machine-readable `.result.json` records under `./readiness-evidence/`. The result JSON is created for both PASS and FAIL and has its own checksum sidecar.

The protected readiness workflow reads these immutable records from Cloudflare R2 using a dedicated **read-only** credential. It selects the latest result by `finishedAtUtc` (so a newer FAIL overrides an older PASS), verifies the result and referenced report/bundle bytes, checks release identity/freshness/reviewer requirements, and reads RPO/RTO only from the reviewed recovery result. A filename, old GitHub variable, or upload timestamp alone is never proof. The GitHub production-release and protected-environment approval gates remain required.

## Current evidence seed for this deployment

The environment-variable seed below is retained for local bookkeeping and compatibility with the legacy publisher; it is **not** read by the new R2 aggregator. Existing text reports without a matching `.result.json` and sidecar are not eligible for the new gate. Re-run the relevant collectors (or attach a real reviewed source report using `record-reviewed-evidence.sh` where supported) to create new records. Do not copy or promote old PASS values into fresh results:

```bash
mkdir -p readiness-evidence
cp infra/readiness/current-evidence-seed.env.example readiness-evidence/variables.env
```

The seed intentionally omits offsite-backup status until the new lock/access review is completed, and omits payment-chaos, independent penetration-test, finance, privacy and MFA results until real evidence exists. If the evidence path already exists in R2, verify the object and `.sha256` sidecar before using it.

## Evidence scripts

| Variables | Script | Scope and requirements |
|---|---|---|
| `OFFSITE_BACKUP_*` | `offsite-backup-review.sh` | Read-only R2 check plus operator review of current Bucket Lock, token scope, access and recovery path. Never attempts overwrite/delete. |
| `SINGLE_NODE_RECOVERY_*`, `PITR_*`, `ALERTING_*` | `record-reviewed-evidence.sh` | Bundles a real existing drill/report with an explicit named review, scope, SHA and (for recovery) measured RPO/RTO. It does not perform the drill or auto-approve the result. |
| Protected readiness gate | `infra/certification/aggregate_readiness_evidence.py` | Read-only R2 aggregation with checksum, freshness, release-SHA and reviewer checks; outputs a manifest only when all nine controls pass. `--audit-only` never writes a certification manifest. |
| `PAYMENT_CHAOS_*` | `payment-chaos.sh` | Staging only; provider sandbox; matching release SHA and E2E SHA; `c1.*` through `c9.*` notes/screenshots/logs; disposable data. |
| `MFA_*` | `mfa-verification.sh` | Staging pre-deploy; matching staging env file; schema must contain MFA columns; wrong-code/replay/recovery checklist. Do not run against the pre-migration V32 production database. |
| `FINANCE_RECONCILIATION_*` | `finance-reconciliation.sh` | Provider dashboard/settlement totals for the same period; database read-only role; independent reviewer different from operator. |
| `PRIVACY_REVIEW_*` | `privacy-review.sh` | Staging public `/privacy`, `/terms`, `/refund-policy` pages; logs; named business/legal reviewer different from operator. A script cannot approve legal content. |
| `SECURITY_SELF_ASSESSMENT_*` | `security-assessment.sh` with `ASSESSMENT_TYPE=self-run` | Self-run DAST/header/API checks only. Does **not** set `PEN_TEST_STATUS`. |
| `PEN_TEST_*` | `security-assessment.sh` with `ASSESSMENT_TYPE=independent` | Requires a real external assessor report, distinct assessor/reviewer/operator names, matching `INDEPENDENT_PEN_TEST_RELEASE_SHA`, `INDEPENDENT_PEN_TEST_REVIEWED=yes`, parsed matching-target ZAP JSON, explicit findings CSV and scope checklist. Preserve the assessor report. |
| `DUMP_RESTORE_DRILL_*` and `recovery-drill-candidates-*.env` | `recovery-drill.sh` | Dump restore to a disposable PostgreSQL DB. Its measured candidates do **not** overwrite the already-reviewed `RPO_SECONDS`/`RTO_SECONDS`; the scopes are not automatically interchangeable. |
| GitHub variable publication | `publish-variables.sh` | Checks all required values, uploads or verifies each R2 object and sidecar, reads them back and verifies SHA-256. GitHub publication is separately gated. |

## R2 settings for backup review (existing bucket)

Keep the existing backup profile and bucket for backup/PITR operations. Do not print or paste credentials.

```bash
export AWS_PROFILE=neelastack-r2
export R2_BUCKET=neelastack-prod-db-backups
export R2_ENDPOINT_URL=https://058bc80c5f25ec698f8aa691f0750603.r2.cloudflarestorage.com
```

Run the updated offsite review with the existing non-destructive lock-test object:

```bash
OPERATOR='Your Name' \
R2_LOCK_TEST_KEY='production/pitr-stream/lock-validation/lock-test-20261023T031254Z-1984425.txt' \
bash infra/readiness/offsite-backup-review.sh
```

Answer each item only after confirming it in the Cloudflare dashboard and retained lock-test evidence. The script only reads the test object; it never performs a test overwrite or deletion.

## Dedicated R2 readiness evidence bucket (new)

Create the separate bucket `neelastack-readiness-evidence` and configure a server-side R2 Bucket Lock/retention rule for the `production/readiness-evidence/` object prefix. Do not store these results in the backup bucket. Cloudflare's standard S3-compatible Object Read Only and Object Read & Write credentials can be scoped to specific buckets; the separate bucket avoids giving the GitHub aggregator read access to database backups. See the [Cloudflare R2 token documentation](https://developers.cloudflare.com/r2/api/tokens/).

Create two bucket-scoped tokens:
- **Read only**: for the GitHub Environments only; used to list/read evidence objects and sidecars, with no write/delete permission.
- **Read & Write**: for local publishing and the staging payment-chaos runner, scoped only to the evidence bucket.

Configure the read/write token as AWS CLI profile `neelastack-r2-evidence` on both the Windows authoring machine and the staging VM. Keep the existing `neelastack-r2` profile for backups. Do not print credentials, commit them, or reuse the backup profile for the readiness uploader.

## Docker-only PostgreSQL connections

Use `PSQL_CMD` for a container-only DB; finance evidence must use a **read-only database role**. If staging does not already have one, create it on staging only. Connect interactively to the staging database as its database owner/admin:

```bash
docker exec -it lakhdatar-staging-postgres psql -U lakhdatar -d lakhdatar
```

Then execute these statements in `psql` (the final `\password` prompts without echoing the password):

```sql
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'readiness_readonly') THEN
    CREATE ROLE readiness_readonly LOGIN;
  END IF;
END $$;
GRANT CONNECT ON DATABASE lakhdatar_staging TO readiness_readonly;
GRANT USAGE ON SCHEMA public TO readiness_readonly;
GRANT SELECT ON TABLE
  public.refunds, public.financial_ledger_entries, public.payments,
  public.orders, public.order_items, public.tickets, public.ticket_types,
  public.ticket_checkins, public.payment_webhook_events
TO readiness_readonly;
-- Set a password only if you need to connect over the Docker bridge/network.
-- For container-local psql, prefer socket/trust within the isolated container.
\password readiness_readonly
```

Use that role for the checks:

```bash
PSQL_CMD='docker exec -i lakhdatar-staging-postgres psql -U readiness_readonly -d lakhdatar_staging'
```

Do not claim the read-only-role requirement is met by using the application's privileged `lakhdatar` role. Finance checks fail if the connected role is elevated or has write privileges on public tables. These DB checks need a schema migrated through the required V33–V41 columns; use staging or the isolated V41 recovery copy, never the V32 production DB for those queries.

## Payment-chaos proof requirements

Complete `PAYMENT-CHAOS-RUNBOOK.md` against a disposable staging event and sandbox credentials. Save one dated, nonempty file for each scenario as `c1.txt` … `c9.txt` (or `c1.png`, `c2.log`, etc.) in a private directory. Record scenario time, order reference (redact customer data), action, expected outcome, observed outcome and supporting screenshot/log filename. Do not put bearer tokens, MFA seeds, customer credentials, or provider secrets into evidence. The script requires the E2E run SHA to exactly match the release SHA under qualification.

Example launch (run only after completing all scenarios and preparing notes):

```bash
CONFIRM_STAGING_SANDBOX=yes \
PSQL_CMD='docker exec -i lakhdatar-staging-postgres psql -U readiness_readonly -d lakhdatar_staging' \
RELEASE_SHA='<exact-qualified-staging-SHA>' \
E2E_RUN_SHA='<same-exact-SHA-used-by-the-passing-E2E-run>' \
E2E_RUN_URL='https://github.com/cyberhackstar/lakhdatar-events/actions/runs/<actual-passing-E2E-run-id>' \
SCENARIO_EVIDENCE_DIR='./payment-chaos-evidence' OPERATOR='Your Name' \
bash infra/readiness/payment-chaos.sh
```

Change the SHA/run URL if the actual test run used another release; they must match exactly. The example does not mean any scenario has already passed.

## RPO/RTO interpretation

Do not use the 159-second routine-window WAL-object visibility sample as transaction-level disaster-recovery RPO. Full recovery timing candidates vary by drill (93/455/503 seconds) and scope; the reviewed existing-VM scope excludes replacement VM, DNS and Cloudflare Tunnel cutover. The new gate does not read free-standing `RPO_SECONDS` or `RTO_SECONDS` variables: those values must be attached to a current, named `SINGLE_NODE_RECOVERY` result after an authorized reviewer accepts the exact measurement and scope. The result must document at least 30 characters of scope/limitations, provide measured integer seconds, and stay within the reviewed policy maxima (300-second RPO, 1,800-second RTO). `recovery-drill.sh` creates separate disposable-dump-restore candidates and must not be used to silently overwrite the reviewed scope measurements.

## Publishing evidence

When a control finishes, `lib.sh` writes:
- the human-readable report or evidence bundle plus `.sha256`;
- the result record `*.result.json` plus its own `.sha256`;
- current local `*_STATUS`, `*_EVIDENCE_ID`, and `*_RESULT_ID` values in `readiness-evidence/variables.env`.

A failed run also emits a result with `status=FAIL`; keep and upload that record rather than hiding it behind an older PASS. Do not edit result JSON or checksums by hand.

Use the dedicated evidence-bucket writer profile only to upload/read back objects; it must not read or write the backup bucket:

```bash
R2_EVIDENCE_BUCKET=neelastack-readiness-evidence \
R2_ENDPOINT_URL=https://058bc80c5f25ec698f8aa691f0750603.r2.cloudflarestorage.com \
AWS_PROFILE=neelastack-r2-evidence UPLOAD_EVIDENCE=yes \
  bash infra/readiness/publish-variables.sh readiness-evidence/variables.env
```

The publisher checks the report/result sidecars, downloads each object back, verifies bytes, and refuses to overwrite an immutable/locked object with different content. It can publish partial evidence objects while the overall assessment remains blocked. `APPLY=yes` is legacy GitHub-variable publication and is **not required by the new readiness workflow**; do not use it as a substitute for source evidence or human review.

The aggregation workflows require these settings in both GitHub Environments:
- Protected Environment `single-node-production-readiness`: `R2_EVIDENCE_BUCKET=neelastack-readiness-evidence`, `R2_ENDPOINT_URL` variables, and `R2_EVIDENCE_READONLY_ACCESS_KEY_ID`, `R2_EVIDENCE_READONLY_SECRET_ACCESS_KEY` secrets. Keep required reviewer approval enabled.
- Environment `readiness-evidence-audit`: the same two variables and the same bucket-scoped read-only secrets, with no approval rule so the weekly read-only audit can run unattended.

The GitHub token has access only to the dedicated readiness evidence bucket and does not have backup bucket access or write/delete rights. The writer profile used by `publish-variables.sh` and the PowerShell payment-chaos runner is separate and scoped to the same evidence bucket. Because the token is bucket-scoped, do not rely on a key-prefix restriction to protect backups stored in a different bucket.

## Correct sequence

1. Update the offsite backup/lock review and create fresh report + result JSON + sidecars.
2. Complete all nine staging payment-chaos scenarios with disposable data and sandbox payment credentials; **the currently observed CANCELLED/REFUNDED order with zero tickets is not a PASS**. Re-run on a new valid fixture only after the C2 preconditions are corrected.
3. Complete the MFA replay and second-admin recovery checks on staging, using the exact qualified release SHA.
4. Complete provider-versus-ledger finance reconciliation using a read-only DB role and an independent reviewer.
5. Publish and review the privacy/legal pages, attaching a named business/legal reviewer.
6. Attach a real external penetration-test report and explicit findings review; self-run DAST is supplementary and does not satisfy PEN_TEST.
7. Exercise database point-in-time and application recovery with documented scope. Use `record-reviewed-evidence.sh` only to attach real existing evidence and the explicit independent review; do not invent numbers or promote incompatible dump-restore candidate values into RPO/RTO.
8. After each real run/review, upload the generated report/result/checksum set to R2 with `UPLOAD_EVIDENCE=yes`. A local report or a successful upload is not a PASS by itself.
9. Run **Single-Node Production Readiness** with `release_sha=<exact SHA in the passing Enterprise Release Qualification manifest>` and `qualification_run_id=<exact successful qualification Actions run ID>`. The aggregator verifies current R2 records and produces the artifact only if all required controls pass. Inspect the readiness manifest and evidence report before the separate protected Production workflow. The scheduled weekly audit reports missing, failed, mismatched or stale evidence but never certifies or deploys.

No part of this kit changes production application code or the production database. Single-node readiness does not certify HA, and it does not prove recovery after total VM loss, replacement VM, DNS or Cloudflare Tunnel cutover.

## Read-only evidence inventory from Windows

The packaged `single-node-evidence-inventory.ps1` uses the existing `ssh vm` alias, lists candidate R2/local evidence filenames, and does not change database state, deployment state, GitHub Variables, or readiness statuses. Run it from the repository root in PowerShell:

```powershell
.\infra\readiness\single-node-evidence-inventory.ps1
```

The listing is only an inventory: inspect each report and SHA-256 sidecar and obtain the required reviewer approval before publishing an evidence ID or setting a status to `PASS`.
