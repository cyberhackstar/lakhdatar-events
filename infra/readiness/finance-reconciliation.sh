#!/usr/bin/env bash
# Finance reconciliation evidence: DB invariants + control totals compared with the payment provider's own totals.
#
#   DATABASE_URL=postgresql://readonly_user:pw@host/db PERIOD_FROM=2026-10-01 PERIOD_TO=2026-10-22 \
#   PROVIDER=CASHFREE PROVIDER_SALES_MINOR=0 PROVIDER_REFUNDS_MINOR=0 OPERATOR='Name' \
#   bash infra/readiness/finance-reconciliation.sh
# PROVIDER_*_MINOR come from the provider dashboard/settlement report for the same period (paise). Use 0 only if the
# dashboard really shows no activity. Use a READ-ONLY database role.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "$SCRIPT_DIR/lib.sh"
build_psql
: "${PERIOD_FROM:?PERIOD_FROM (YYYY-MM-DD) is required}"; : "${PERIOD_TO:?PERIOD_TO (YYYY-MM-DD) is required}"
: "${RELEASE_SHA:?RELEASE_SHA (exact app release checked by qualification/staging) is required}"
: "${PROVIDER:?PROVIDER (CASHFREE or RAZORPAY) is required}"
[[ "${PROVIDER_SALES_MINOR:-}" =~ ^[0-9]+$ && "${PROVIDER_REFUNDS_MINOR:-}" =~ ^[0-9]+$ ]] || { echo "Set PROVIDER_SALES_MINOR and PROVIDER_REFUNDS_MINOR (integers, paise) from the provider's report for the same period." >&2; exit 2; }
STALE_HOURS="${STALE_HOURS:-24}"
begin_report finance-reconciliation FINANCE_RECONCILIATION "Finance reconciliation"
FINANCE_REVIEWER="${FINANCE_REVIEWER:-}"
if [[ -z "$FINANCE_REVIEWER" ]]; then
  [[ -t 0 ]] || { fail "FINANCE_REVIEWER (a named reviewer other than OPERATOR) is required"; FINANCE_REVIEWER="UNSET"; }
  if [[ -t 0 ]]; then read -r -p "Independent finance reviewer name (must differ from operator): " FINANCE_REVIEWER; fi
fi
if [[ -n "$FINANCE_REVIEWER" && "$FINANCE_REVIEWER" != "UNSET" && "${FINANCE_REVIEWER,,}" != "${OPERATOR,,}" ]]; then
  pass "Independent finance reviewer recorded: $FINANCE_REVIEWER"
else
  fail "FINANCE_REVIEWER must be set to a named person other than OPERATOR"
fi
say "Period: $PERIOD_FROM to $PERIOD_TO (exclusive) | Provider: $PROVIDER"
PSQLOPTS=(-v ON_ERROR_STOP=1 -X -At -F'|')
ROLE_FLAGS="$("${PSQL[@]}" -X -At -F'|' -qc "select rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls from pg_roles where rolname=current_user")"
if [[ "$ROLE_FLAGS" == "f|f|f|f|f" ]]; then pass "DB role is not superuser/role-creator/DB-creator/replication/BYPASSRLS"
else fail "DB connection role is elevated or role metadata could not be verified (flags=$ROLE_FLAGS); use a dedicated read-only role"; fi
WRITE_PRIVS="$("${PSQL[@]}" -X -Atqc "select coalesce(bool_or(has_table_privilege(current_user, format('%I.%I', schemaname, tablename), 'INSERT') or has_table_privilege(current_user, format('%I.%I', schemaname, tablename), 'UPDATE') or has_table_privilege(current_user, format('%I.%I', schemaname, tablename), 'DELETE') or has_table_privilege(current_user, format('%I.%I', schemaname, tablename), 'TRUNCATE') or has_table_privilege(current_user, format('%I.%I', schemaname, tablename), 'REFERENCES') or has_table_privilege(current_user, format('%I.%I', schemaname, tablename), 'TRIGGER')), false) from pg_tables where schemaname='public'")"
[[ "$WRITE_PRIVS" == f ]] && pass "DB role has no write privileges on public tables" || fail "DB role has write privileges or could not be verified (write_privileges=$WRITE_PRIVS); use a dedicated read-only role"
say ""; say "== Invariants =="
OUT="$({ echo 'BEGIN READ ONLY;'; cat "$SCRIPT_DIR/finance-invariants.sql"; } | "${PSQL[@]}" "${PSQLOPTS[@]}" -v stale_hours="$STALE_HOURS" 2>&1)" || { fail "invariant query failed: $OUT"; OUT=""; }
while IFS='|' read -r name sev n; do
  [[ -z "$name" || "$name" == BEGIN ]] && continue
  if [[ "$n" == 0 ]]; then pass "$name = 0"
  elif [[ "$sev" == FAIL ]]; then fail "$name = $n"
  else
    if [[ "${ACCEPT_WARNINGS:-}" == *"$name"* ]]; then info "$name = $n (accepted by reviewer via ACCEPT_WARNINGS)"; else fail "$name = $n (review, resolve, or list in ACCEPT_WARNINGS with a note in the checklist)"; fi
  fi
done <<< "$OUT"
say ""; say "== Control totals (ledger) =="
TOT="$( { echo 'BEGIN READ ONLY;'; cat "$SCRIPT_DIR/finance-totals.sql"; } | "${PSQL[@]}" "${PSQLOPTS[@]}" -v from="$PERIOD_FROM" -v to="$PERIOD_TO" 2>&1)" || { fail "totals query failed: $TOT"; TOT=""; }
say "provider|currency|sale_count|sales_minor|refund_count|refunds_minor|net_minor"; say "$TOT"
DB_SALES="$(awk -F'|' -v p="$PROVIDER" '$1==p{s+=$4}END{print s+0}' <<< "$TOT")"
DB_REFUNDS="$(awk -F'|' -v p="$PROVIDER" '$1==p{s+=$6}END{print s+0}' <<< "$TOT")"
say "Database sales: $DB_SALES | provider-reported sales: $PROVIDER_SALES_MINOR"
say "Database refunds: $DB_REFUNDS | provider-reported refunds: $PROVIDER_REFUNDS_MINOR"
[[ "$DB_SALES" == "$PROVIDER_SALES_MINOR" ]] && pass "sales match provider" || fail "sales differ from provider by $(( DB_SALES - PROVIDER_SALES_MINOR )) minor units"
[[ "$DB_REFUNDS" == "$PROVIDER_REFUNDS_MINOR" ]] && pass "refunds match provider" || fail "refunds differ from provider by $(( DB_REFUNDS - PROVIDER_REFUNDS_MINOR )) minor units"
say ""; say "== Reviewer checklist =="
checklist "$SCRIPT_DIR/checklists/finance.txt"
finish_report
