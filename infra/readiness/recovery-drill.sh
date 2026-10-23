#!/usr/bin/env bash
# Single-node recovery drill + measured RPO/RTO.
# Restores the newest (or given) backup into a DISPOSABLE PostgreSQL server, verifies integrity,
# and measures RTO and RPO. Never touches the production database.
#
#   DRILL_CONFIRM=RESTORE-DRILL DISPOSABLE_DATABASE_URL=postgresql://user:pw@host:5432/postgres \
#   BACKUP_DIR=/path/to/backups OPERATOR='Name' bash infra/readiness/recovery-drill.sh [dump]
#
# RTO = measured restore + verification time + MANUAL_RTO_SECONDS (time you measured for the human/VM steps
# the script cannot do: provisioning a replacement VM, restoring env/secrets, starting containers, DNS/tunnel).
# RPO = age of the newest recovery point at drill start (newest dump; or WAL archive age when PITR_PG_URL is set).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "$SCRIPT_DIR/lib.sh"
[[ "${DRILL_CONFIRM:-}" == "RESTORE-DRILL" ]] || { echo "Set DRILL_CONFIRM=RESTORE-DRILL." >&2; exit 2; }
BASE_URL="${DISPOSABLE_DATABASE_URL:-}"; [[ -n "$BASE_URL" ]] || { echo "DISPOSABLE_DATABASE_URL is required (a throwaway server, never production)." >&2; exit 2; }
BASE_URL="${BASE_URL#jdbc:}"
BACKUP_DIR="${BACKUP_DIR:-$PWD/infra/backups}"
DUMP="${1:-$(ls -1t "$BACKUP_DIR"/*.dump 2>/dev/null | head -1 || true)}"
[[ -f "$DUMP" ]] || { echo "No backup dump found (pass a path or set BACKUP_DIR)." >&2; exit 2; }
MANUAL_RTO_SECONDS="${MANUAL_RTO_SECONDS:-}"
[[ "$MANUAL_RTO_SECONDS" =~ ^[0-9]+$ ]] || { echo "Set MANUAL_RTO_SECONDS to the measured seconds for manual recovery steps (VM/env/containers/DNS). Time them once, honestly." >&2; exit 2; }
MAX_RPO_SECONDS="${MAX_RPO_SECONDS:-300}"; MAX_RTO_SECONDS="${MAX_RTO_SECONDS:-1800}"
for c in pg_restore psql sha256sum; do command -v "$c" >/dev/null || { echo "$c is required." >&2; exit 2; }; done

begin_report recovery-drill DUMP_RESTORE_DRILL "Disposable database dump-restore drill (separate from existing-VM recovery certification)"
DB="drill_$(stamp | tr 'A-Z' 'a-z')"
# Build per-database URL (replace path component only)
DB_URL="$(python3 - "$BASE_URL" "$DB" <<'PY'
import sys, urllib.parse as u
p=u.urlsplit(sys.argv[1]); print(u.urlunsplit(p._replace(path='/'+sys.argv[2])))
PY
)"
T0=$(date +%s)
say "Backup under test: $(basename "$DUMP") ($(stat -c %s "$DUMP") bytes, modified $(date -u -d @"$(stat -c %Y "$DUMP")" +%FT%TZ))"

# --- RPO ---
DUMP_AGE=$(( T0 - $(stat -c %Y "$DUMP") )); RPO="$DUMP_AGE"; RPO_BASIS="newest backup age"
if [[ -n "${PITR_PG_URL:-}" ]]; then
  W="$(psql "$PITR_PG_URL" -Atqc "select coalesce(extract(epoch from (now()-last_archived_time))::bigint, 999999999) from pg_stat_archiver" 2>/dev/null || true)"
  if [[ "$W" =~ ^[0-9]+$ ]]; then RPO="$W"; RPO_BASIS="WAL archive age (pg_stat_archiver)"; fi
fi
# The age above is a sample at drill time. The true worst case is the gap between recovery points, so take the max
# of the sample and the configured gap (WAL archive_timeout, or BACKUP_INTERVAL_SECONDS for dump-only backups).
GAP=""
if [[ -n "${PITR_PG_URL:-}" ]]; then GAP="$(psql "$PITR_PG_URL" -Atqc "select extract(epoch from nullif(current_setting('archive_timeout'),'0')::interval)::bigint" 2>/dev/null || true)"; fi
[[ "$GAP" =~ ^[0-9]+$ ]] || GAP="${BACKUP_INTERVAL_SECONDS:-}"
if [[ "$GAP" =~ ^[0-9]+$ ]]; then (( GAP > RPO )) && { RPO="$GAP"; RPO_BASIS="$RPO_BASIS, raised to configured recovery-point gap"; }
else info "No archive_timeout/BACKUP_INTERVAL_SECONDS available: RPO is only the sampled age, not a proven worst case."; fi
say "Measured RPO: ${RPO}s (basis: $RPO_BASIS)"
(( RPO <= MAX_RPO_SECONDS )) && pass "RPO ${RPO}s <= ${MAX_RPO_SECONDS}s" || fail "RPO ${RPO}s exceeds ${MAX_RPO_SECONDS}s (back up more often or enable WAL archiving and set PITR_PG_URL)"

# --- Integrity + restore ---
if [[ -f "$DUMP.sha256" ]]; then ( cd "$(dirname "$DUMP")" && sha256sum -c "$(basename "$DUMP").sha256" >/dev/null 2>&1 ) && pass "backup checksum verified" || fail "backup checksum mismatch"; else fail "no .sha256 file next to the backup"; fi
psql "$BASE_URL" -v ON_ERROR_STOP=1 -qc "CREATE DATABASE $DB" >/dev/null && pass "disposable database $DB created"
R0=$(date +%s)
if pg_restore --no-owner --no-privileges --exit-on-error -d "$DB_URL" "$DUMP" >>"$REPORT" 2>&1; then pass "pg_restore completed in $(( $(date +%s)-R0 ))s"; else fail "pg_restore failed (see report)"; fi

q() { psql "$DB_URL" -Atqc "$1" 2>/dev/null || echo ERR; }
for t in events orders payments refunds tickets users financial_ledger_entries audit_logs; do
  [[ "$(q "select to_regclass('public.$t') is not null")" == t ]] && pass "table $t present ($(q "select count(*) from $t") rows)" || fail "table $t missing"
done
FW="$(q "select count(*) from flyway_schema_history where success")"; [[ "$FW" =~ ^[1-9][0-9]*$ ]] && pass "Flyway history: $FW successful migrations" || fail "Flyway history missing"
FWF="$(q "select count(*) from flyway_schema_history where not success")"; [[ "$FWF" == 0 ]] && pass "no failed migrations" || fail "$FWF failed migrations in restored history"
ORPH="$(q "select count(*) from payments p left join orders o on o.id=p.order_id where o.id is null")"; [[ "$ORPH" == 0 ]] && pass "no orphan payments" || fail "$ORPH orphan payments"
ORPHT="$(q "select count(*) from tickets t left join orders o on o.id=t.order_id where o.id is null")"; [[ "$ORPHT" == 0 ]] && pass "no orphan tickets" || fail "$ORPHT orphan tickets"
LEDGER="$(q "select count(*) from payments p where p.status in ('CAPTURED','COMPLETED') and not exists (select 1 from financial_ledger_entries l where l.payment_id=p.id and l.entry_type='SALE')")"
[[ "$LEDGER" == 0 ]] && pass "every captured payment has a ledger SALE entry" || fail "$LEDGER captured payments lack ledger entries"
psql "$BASE_URL" -qc "DROP DATABASE $DB" >/dev/null 2>&1 && pass "disposable database dropped" || info "could not drop $DB; remove it manually"

# --- RTO ---
MEASURED=$(( $(date +%s) - T0 ))
RTO=$(( MEASURED + MANUAL_RTO_SECONDS ))
say "Measured restore+verify: ${MEASURED}s; manual steps: ${MANUAL_RTO_SECONDS}s; RTO: ${RTO}s"
(( RTO <= MAX_RTO_SECONDS )) && pass "RTO ${RTO}s <= ${MAX_RTO_SECONDS}s" || fail "RTO ${RTO}s exceeds ${MAX_RTO_SECONDS}s"

if finish_report; then
  # This exercise restores a dump into a throwaway database and adds a separately timed manual allowance.
  # It is not automatically interchangeable with commit-to-offsite latency or an existing-VM recovery drill.
  CANDIDATES="$EVIDENCE_DIR/recovery-drill-candidates-$(stamp).env"
  {
    printf 'DUMP_RESTORE_RPO_CANDIDATE_SECONDS=%s\n' "$RPO"
    printf 'DUMP_RESTORE_RTO_CANDIDATE_SECONDS=%s\n' "$RTO"
    printf 'MEASUREMENT_SCOPE=disposable-dump-restore-plus-manual-allowance\n'
    printf 'EVIDENCE_ID=%s/%s\n' "$EVIDENCE_PREFIX" "$(basename "$REPORT")"
  } > "$CANDIDATES"
  chmod 600 "$CANDIDATES"
  echo "Candidate measurements written to $CANDIDATES. RPO_SECONDS/RTO_SECONDS were intentionally not changed."
fi
