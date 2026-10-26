#!/usr/bin/env bash
# Shared helpers for the single-node readiness kit. Source this file; do not execute it.
# Evidence is written to $EVIDENCE_DIR (default ./readiness-evidence). Each report is hashed, and the
# GitHub Environment variable values for the item are appended to $EVIDENCE_DIR/variables.env.
set -Eeuo pipefail
EVIDENCE_DIR="${EVIDENCE_DIR:-$PWD/readiness-evidence}"
EVIDENCE_PREFIX="${EVIDENCE_PREFIX:-production/readiness-evidence}"
OPERATOR="${OPERATOR:-}"
mkdir -p "$EVIDENCE_DIR"; chmod 700 "$EVIDENCE_DIR" 2>/dev/null || true
FAILS=0; REPORT=""; ITEM=""; VARPREFIX=""

now_utc() { date -u +%Y-%m-%dT%H:%M:%SZ; }
stamp()   { date -u +%Y%m%dT%H%M%SZ; }
say()     { printf '%s\n' "$*" | tee -a "$REPORT"; }
pass()    { say "PASS  $*"; }
fail()    { say "FAIL  $*"; FAILS=$((FAILS+1)); }
info()    { say "INFO  $*"; }

need_operator() {
  if [[ -z "$OPERATOR" ]]; then
    [[ -t 0 ]] || { echo "Set OPERATOR='Your Name' (the accountable reviewer)." >&2; exit 2; }
    read -r -p "Reviewer name (recorded in the evidence): " OPERATOR
  fi
  [[ -n "$OPERATOR" ]] || { echo "Reviewer name is required." >&2; exit 2; }
}

# begin_report <item-slug> <VARIABLE_PREFIX> <title>
begin_report() {
  ITEM="$1"; VARPREFIX="$2"; need_operator
  REPORT="$EVIDENCE_DIR/${ITEM}-$(stamp).txt"; : > "$REPORT"; chmod 600 "$REPORT"
  REPORT_STARTED_AT="$(now_utc)"
  say "# $3"
  say "Item: $ITEM"; say "Started (UTC): $REPORT_STARTED_AT"; say "Reviewer: $OPERATOR"
  say "Release SHA: ${RELEASE_SHA:-unspecified}"; say "Host: $(hostname)"; say ""
}

# checklist <file>: each line "id|question". Answers come from $ANSWERS_FILE (id=yes|no|na:note) or the terminal.
# Only "yes" passes. "na" needs a note of at least 20 characters and is listed in the report.
checklist() {
  local file="$1" id q ans note
  while IFS='|' read -r id q; do
    [[ -z "$id" || "$id" == \#* ]] && continue
    ans=""
    if [[ -n "${ANSWERS_FILE:-}" && -f "$ANSWERS_FILE" ]]; then ans="$(grep -E "^${id}=" "$ANSWERS_FILE" | tail -1 | cut -d= -f2- || true)"; fi
    if [[ -z "$ans" ]]; then
      if [[ -t 0 ]]; then printf '\n[%s] %s\n  Answer yes / no / na:<reason>: ' "$id" "$q" >&2; read -r ans
      else ans="no:unanswered"; fi
    fi
    note="${ans#*:}"; [[ "$ans" == *:* ]] || note=""
    case "${ans%%:*}" in
      yes) pass "[$id] $q${note:+ ($note)}" ;;
      na)  if (( ${#note} >= 20 )); then info "[$id] N/A: $q -- $note"; else fail "[$id] N/A needs a reason of at least 20 characters: $q"; fi ;;
      *)   fail "[$id] $q${note:+ -- $note}" ;;
    esac
  done < "$file"
}

# set_var NAME VALUE -> variables.env
set_var() {
  local f="$EVIDENCE_DIR/variables.env"; touch "$f"; chmod 600 "$f"
  grep -v "^$1=" "$f" > "$f.tmp" || true; printf '%s=%s\n' "$1" "$2" >> "$f.tmp"; mv "$f.tmp" "$f"
}

# finish_report [extra-file-to-bundle ...]
# Every run, including FAIL, produces an immutable-friendly result JSON plus SHA-256 sidecar.
# The status is always updated in variables.env so a failed rerun cannot leave a stale PASS locally.
finish_report() {
  local finished_at status="PASS" file="$REPORT" report_sha result_file result_key
  finished_at="$(now_utc)"
  say ""; say "Finished (UTC): $finished_at"
  (( FAILS > 0 )) && status="FAIL"
  say "Result: $status ($FAILS failing check(s))"
  if (( $# )); then
    local bundle="${REPORT%.txt}.tar.gz"
    tar -czf "$bundle" -C "$EVIDENCE_DIR" "$(basename "$REPORT")" "$@" 2>/dev/null && file="$bundle"
  fi
  ( cd "$EVIDENCE_DIR" && sha256sum "$(basename "$file")" > "$(basename "$file").sha256" )
  report_sha="$(cut -d' ' -f1 "$EVIDENCE_DIR/$(basename "$file").sha256")"
  printf 'Evidence file: %s  sha256=%s\n' "$(basename "$file")" "$report_sha"
  local id="$EVIDENCE_PREFIX/$(basename "$file")"
  result_file="${REPORT%.txt}.result.json"
  result_key="$EVIDENCE_PREFIX/$(basename "$result_file")"
  python3 - "$result_file" "$ITEM" "$VARPREFIX" "$status" "$REPORT_STARTED_AT" "$finished_at" "$OPERATOR" "$id" "$report_sha" "$(basename "$file")" "$FAILS" "$REPORT" <<'PYRESULT'
import json, os, re, sys
from pathlib import Path

(out, item, control_id, status, started, finished, reviewer, evidence_key,
 report_sha, report_name, fail_count, report_path) = sys.argv[1:]
def optional(name):
    value = os.environ.get(name, "").strip()
    return value or None
def number(name):
    value = optional(name)
    if value is None:
        return None
    return int(value) if re.fullmatch(r"[0-9]{1,9}", value) else value
report = Path(report_path)
try:
    text = report.read_text(encoding="utf-8", errors="replace")
    pass_count = sum(1 for line in text.splitlines() if re.match(r"^PASS\s", line))
    fail_lines = sum(1 for line in text.splitlines() if re.match(r"^FAIL\s", line))
except Exception:
    pass_count = 0
    fail_lines = int(fail_count)
metrics = {}
for source, target in (("RESULT_RPO_SECONDS", "rpoSeconds"), ("RESULT_RTO_SECONDS", "rtoSeconds")):
    value = number(source)
    if value is not None:
        metrics[target] = value
control = {
    "schemaVersion": 1,
    "kind": "readiness-evidence-result",
    "controlId": control_id,
    "item": item,
    "status": status,
    "startedAtUtc": started,
    "finishedAtUtc": finished,
    "releaseSha": optional("RELEASE_SHA") if re.fullmatch(r"[0-9a-f]{40}", os.environ.get("RELEASE_SHA", "")) else None,
    "reviewer": reviewer,
    "independentReviewer": optional("INDEPENDENT_REVIEWER") or (
        optional("INDEPENDENT_PEN_TEST_REVIEWER") if control_id == "PEN_TEST" else
        optional("FINANCE_REVIEWER") if control_id == "FINANCE_RECONCILIATION" else
        optional("PRIVACY_REVIEWER") if control_id == "PRIVACY_REVIEW" else None
    ),
    "assessor": optional("INDEPENDENT_ASSESSOR_NAME") if control_id == "PEN_TEST" else None,
    "assessmentType": optional("ASSESSMENT_TYPE") if control_id == "PEN_TEST" else None,
    "evidence": {
        "key": evidence_key,
        "fileName": report_name,
        "sha256": report_sha,
    },
    "metrics": metrics,
    "checks": {"passCount": pass_count, "failCount": fail_lines},
    "automation": {
        "repository": optional("GITHUB_REPOSITORY"),
        "workflow": optional("GITHUB_WORKFLOW"),
        "runId": optional("GITHUB_RUN_ID"),
        "runUrl": (
            os.environ.get("GITHUB_SERVER_URL", "").rstrip("/") + "/" +
            os.environ.get("GITHUB_REPOSITORY", "") + "/actions/runs/" +
            os.environ.get("GITHUB_RUN_ID", "")
        ) if optional("GITHUB_SERVER_URL") and optional("GITHUB_REPOSITORY") and optional("GITHUB_RUN_ID") else None,
    },
    "productionCertificationGranted": False,
    "humanApprovalRequired": True,
}
Path(out).write_text(json.dumps(control, indent=2, sort_keys=True) + "\n", encoding="utf-8")
Path(out).chmod(0o600)
PYRESULT
  ( cd "$EVIDENCE_DIR" && sha256sum "$(basename "$result_file")" > "$(basename "$result_file").sha256" )
  set_var "${VARPREFIX}_STATUS" "$status"
  set_var "${VARPREFIX}_EVIDENCE_ID" "$id"
  set_var "${VARPREFIX}_RESULT_ID" "$result_key"
  set_var "${VARPREFIX}_RESULT_SHA256" "$(cut -d' ' -f1 "$EVIDENCE_DIR/$(basename "$result_file").sha256")"
  echo; echo "=> ${VARPREFIX}_STATUS=$status"
  echo "=> ${VARPREFIX}_EVIDENCE_ID=$id"
  echo "=> ${VARPREFIX}_RESULT_ID=$result_key"
  echo "Machine-readable result: $result_file (sha256 sidecar created)."
  if [[ "$status" == PASS ]]; then
    echo "   Evidence is not publishable until publish-variables.sh verifies the report and result JSON in R2."
    return 0
  fi
  echo "NOT PASSING: fix the failures above and re-run. Latest local status is FAIL; older PASS records must not be reused." >&2
  return 1
}

# build_psql: sets PSQL=(...) from PSQL_CMD (e.g. "docker exec -i <container> psql -U lakhdatar -d lakhdatar") or DATABASE_URL.
# PSQL_CMD lets the kit run against a database that is only reachable inside Docker (such as an isolated recovery copy).
build_psql() {
  if [[ -n "${PSQL_CMD:-}" ]]; then read -r -a PSQL <<< "$PSQL_CMD"
  else : "${DATABASE_URL:?Set DATABASE_URL (read-only role) or PSQL_CMD}"; PSQL=(psql "${DATABASE_URL#jdbc:}"); fi
}
