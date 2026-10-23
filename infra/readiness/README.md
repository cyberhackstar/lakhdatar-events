# Single-node production readiness evidence kit

This kit is an **evidence collector**, not an application release or a certification bypass. It writes reports under `./readiness-evidence/`. A local report is not an accepted GitHub evidence ID until `publish-variables.sh` verifies the report and its SHA-256 sidecar in Cloudflare R2. No check is permitted to pass merely because a filename exists.

## Current evidence seed for this deployment

Start from the already-reviewed recovery measurements and report references, then let the new offsite review and completed test scripts update their own fields:

```bash
mkdir -p readiness-evidence
cp infra/readiness/current-evidence-seed.env.example readiness-evidence/variables.env
```

The seed intentionally omits offsite-backup status until the new lock/access review is completed, and omits payment-chaos, independent penetration-test, finance, privacy and MFA results until real evidence exists. If the evidence path already exists in R2, verify the object and `.sha256` sidecar before using it.

## Evidence scripts

| Variables | Script | Scope and requirements |
|---|---|---|
| `OFFSITE_BACKUP_*` | `offsite-backup-review.sh` | Read-only R2 check plus operator review of current Bucket Lock, token scope, access and recovery path. Never attempts overwrite/delete. |
| `PAYMENT_CHAOS_*` | `payment-chaos.sh` | Staging only; provider sandbox; matching release SHA and E2E SHA; `c1.*` through `c9.*` notes/screenshots/logs; disposable data. |
| `MFA_*` | `mfa-verification.sh` | Staging pre-deploy; matching staging env file; schema must contain MFA columns; wrong-code/replay/recovery checklist. Do not run against the pre-migration V32 production database. |
| `FINANCE_RECONCILIATION_*` | `finance-reconciliation.sh` | Provider dashboard/settlement totals for the same period; database read-only role; independent reviewer different from operator. |
| `PRIVACY_REVIEW_*` | `privacy-review.sh` | Staging public `/privacy`, `/terms`, `/refund-policy` pages; logs; named business/legal reviewer different from operator. A script cannot approve legal content. |
| `SECURITY_SELF_ASSESSMENT_*` | `security-assessment.sh` with `ASSESSMENT_TYPE=self-run` | Self-run DAST/header/API checks only. Does **not** set `PEN_TEST_STATUS`. |
| `PEN_TEST_*` | `security-assessment.sh` with `ASSESSMENT_TYPE=independent` | Requires a real external assessor report, distinct assessor/reviewer/operator names, matching `INDEPENDENT_PEN_TEST_RELEASE_SHA`, `INDEPENDENT_PEN_TEST_REVIEWED=yes`, parsed matching-target ZAP JSON, explicit findings CSV and scope checklist. Preserve the assessor report. |
| `DUMP_RESTORE_DRILL_*` and `recovery-drill-candidates-*.env` | `recovery-drill.sh` | Dump restore to a disposable PostgreSQL DB. Its measured candidates do **not** overwrite the already-reviewed `RPO_SECONDS`/`RTO_SECONDS`; the scopes are not automatically interchangeable. |
| GitHub variable publication | `publish-variables.sh` | Checks all required values, uploads or verifies each R2 object and sidecar, reads them back and verifies SHA-256. GitHub publication is separately gated. |

## R2 settings for this account

Use the existing AWS profile on the VM. Do not print or paste credentials.

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

Do not populate `RPO_SECONDS` from the 159-second routine-window WAL-object visibility sample; the accompanying evidence expressly says it is not transaction-level disaster-recovery RPO certification. Full recovery timing candidates vary by drill (93/455/503 seconds) and scope; the reviewed existing-VM scope excludes replacement VM, DNS and Cloudflare Tunnel cutover. Leave the numeric production gate values unset until an authorized reviewer accepts the exact scope and measurement. `recovery-drill.sh` creates separate disposable-dump-restore candidates and does not rewrite certified variables.

## Publishing evidence

Review `readiness-evidence/variables.env` before running any publisher. The file must contain all nine required `*_STATUS`/`*_EVIDENCE_ID` pairs and `RPO_SECONDS`/`RTO_SECONDS`. Add an existing evidence row only when you have verified that its report and checksum are available.

First upload/verify objects in R2 without changing GitHub:

```bash
UPLOAD_EVIDENCE=yes bash infra/readiness/publish-variables.sh readiness-evidence/variables.env
```

The command uses `R2_BUCKET`, `R2_ENDPOINT_URL`, and `AWS_PROFILE` set above. It refuses to overwrite an existing locked object with different bytes, checks the `.sha256` sidecar, downloads the final object and validates it again. If a matching local object is available and its key is new, the report and sidecar are uploaded; if no local object exists, both are downloaded from R2 and verified.

Only after reviewing successful upload/read-back checks should you publish to GitHub. With GitHub CLI installed and authenticated:

```bash
UPLOAD_EVIDENCE=yes APPLY=yes REPO=cyberhackstar/lakhdatar-events \
  bash infra/readiness/publish-variables.sh readiness-evidence/variables.env
```

Without `gh`, use the verified report IDs and values to update the GitHub Environment manually. Do not publish a variable file that lacks verified evidence.

## Correct sequence

1. Update offsite review and retain new report + checksum.
2. Complete staging payment-chaos scenarios and their evidence bundle.
3. Complete MFA replay and second-admin recovery checks on staging.
4. Complete finance reconciliation with provider totals and an independent reviewer.
5. Publish privacy pages and complete the responsible owner's review.
6. Attach a real independent assessment report. Self-run DAST is supplementary, not a substitute.
7. Verify/upload evidence to R2; update GitHub Environment variables only after reviewing it.
8. Run **Single-Node Production Readiness** with `release_sha=fff2632c45dcc9b5b14b5e40190f60d876f8e147` and `qualification_run_id=39570757202` only if every required item is complete and reviewed. Inspect the manifest, then follow the protected production workflow. Do not use an “evidence mode”; the current workflow takes those two required inputs and remains fail-closed.

This kit does not change production application code or the production database. Single-node readiness does not certify HA, nor does it prove recovery after total VM loss.
## Read-only evidence inventory from Windows

The packaged `single-node-evidence-inventory.ps1` uses the existing `ssh vm` alias, lists candidate R2/local evidence filenames, and does not change database state, deployment state, GitHub Variables, or readiness statuses. Run it from the repository root in PowerShell:

```powershell
.\infra\readiness\single-node-evidence-inventory.ps1
```

The listing is only an inventory: inspect each report and SHA-256 sidecar and obtain the required reviewer approval before publishing an evidence ID or setting a status to `PASS`.
