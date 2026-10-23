#!/usr/bin/env bash
# Payment failure-and-recovery evidence. Scenarios run on STAGING with the provider SANDBOX (never live money).
#   CONFIRM_STAGING_SANDBOX=yes DATABASE_URL=<staging read-only url> E2E_RUN_URL=<passing browser-e2e run> OPERATOR='Name' \
#   bash infra/readiness/payment-chaos.sh
# Procedure: infra/readiness/PAYMENT-CHAOS-RUNBOOK.md. After performing the scenarios, this script verifies the staging
# database is internally consistent (no money created or lost, no oversold inventory) and records your answers.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "$SCRIPT_DIR/lib.sh"
[[ "${CONFIRM_STAGING_SANDBOX:-}" == yes ]] || { echo "Set CONFIRM_STAGING_SANDBOX=yes: this evidence must come from staging with provider sandbox credentials." >&2; exit 2; }
build_psql
: "${E2E_RUN_URL:?E2E_RUN_URL (a passing staging browser E2E run) is required}"
: "${RELEASE_SHA:?RELEASE_SHA for the release under qualification is required}"
: "${E2E_RUN_SHA:?E2E_RUN_SHA from the staging E2E run is required}"
: "${SCENARIO_EVIDENCE_DIR:?SCENARIO_EVIDENCE_DIR must contain c1.* through c9.* notes/screenshots/logs}"
[[ "$RELEASE_SHA" =~ ^[0-9a-f]{40}$ && "$E2E_RUN_SHA" =~ ^[0-9a-f]{40}$ && "$RELEASE_SHA" == "$E2E_RUN_SHA" ]] || { echo "E2E_RUN_SHA must exactly match RELEASE_SHA." >&2; exit 2; }
[[ "$E2E_RUN_URL" =~ ^https://github.com/cyberhackstar/lakhdatar-events/actions/runs/[0-9]+$ ]] || { echo "E2E_RUN_URL must be a run URL from this repository." >&2; exit 2; }
if [[ -n "${PSQL_CMD:-}" ]]; then
  [[ "$PSQL_CMD" == *staging* ]] || { echo "Refusing database command without 'staging' in its container/target name. Use the isolated staging PostgreSQL service." >&2; exit 2; }
else
  : "${STAGING_DB_HOST:?When using DATABASE_URL, set STAGING_DB_HOST to the exact staging database hostname}"
  [[ "${DATABASE_URL:-}" == *"$STAGING_DB_HOST"* ]] || { echo "Refusing: DATABASE_URL does not contain STAGING_DB_HOST." >&2; exit 2; }
fi
if [[ -n "${PRODUCTION_DB_HOST:-}" && "${DATABASE_URL:-}${PSQL_CMD:-}" == *"$PRODUCTION_DB_HOST"* ]]; then echo "Refusing: database target points at the production host." >&2; exit 2; fi
begin_report payment-chaos PAYMENT_CHAOS "Payment failure and recovery qualification (staging sandbox)"
say "Passing browser E2E run: $E2E_RUN_URL"
say "Release SHA: $RELEASE_SHA; E2E run SHA: $E2E_RUN_SHA"
pass "E2E run is referenced and SHA matches"
SCENARIO_BUNDLE="payment-chaos-scenarios-$(stamp)"; mkdir -p "$EVIDENCE_DIR/$SCENARIO_BUNDLE"
BUNDLE=("$SCENARIO_BUNDLE")
for scenario in c1 c2 c3 c4 c5 c6 c7 c8 c9; do
  file="$(find "$SCENARIO_EVIDENCE_DIR" -maxdepth 3 -type f -name "$scenario.*" -size +20c -print -quit 2>/dev/null || true)"
  if [[ -n "$file" ]]; then
    cp -- "$file" "$EVIDENCE_DIR/$SCENARIO_BUNDLE/${scenario}-$(basename "$file")"
    sha="$(sha256sum "$EVIDENCE_DIR/$SCENARIO_BUNDLE/${scenario}-$(basename "$file")" | awk '{print $1}')"
    pass "scenario $scenario evidence attached (sha256=$sha)"
  else
    fail "scenario $scenario has no nonempty c${scenario#c}.* file in SCENARIO_EVIDENCE_DIR; include a dated note, screenshot, or log"
  fi
done
OUT="$({ echo 'BEGIN READ ONLY;'; cat "$SCRIPT_DIR/finance-invariants.sql"; } | "${PSQL[@]}" -v ON_ERROR_STOP=1 -v stale_hours=1 -X -At -F'|' 2>&1)" || { fail "invariant query failed: $OUT"; OUT=""; }
while IFS='|' read -r name sev n; do
  [[ -z "$name" || "$name" == BEGIN ]] && continue
  [[ "$n" == 0 ]] && pass "$name = 0" || { [[ "$sev" == FAIL ]] && fail "$name = $n" || fail "$name = $n (chaos runs must leave nothing stuck; wait for recovery jobs or investigate)"; }
done <<< "$OUT"
say ""; say "== Scenarios performed on staging (sandbox) =="
checklist "$SCRIPT_DIR/checklists/payment-chaos.txt"
finish_report "${BUNDLE[@]}"
