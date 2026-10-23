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
  say "# $3"
  say "Item: $ITEM"; say "Started (UTC): $(now_utc)"; say "Reviewer: $OPERATOR"
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
finish_report() {
  say ""; say "Finished (UTC): $(now_utc)"
  local status="PASS"; (( FAILS > 0 )) && status="FAIL"
  say "Result: $status ($FAILS failing check(s))"
  local file="$REPORT"
  if (( $# )); then
    local bundle="${REPORT%.txt}.tar.gz"
    tar -czf "$bundle" -C "$EVIDENCE_DIR" "$(basename "$REPORT")" "$@" 2>/dev/null && file="$bundle"
  fi
  ( cd "$EVIDENCE_DIR" && sha256sum "$(basename "$file")" > "$(basename "$file").sha256" )
  local sha; sha="$(cut -d' ' -f1 "$EVIDENCE_DIR/$(basename "$file").sha256")"
  # Do not append the checksum line to REPORT after hashing it: that would invalidate the sidecar.
  printf 'Evidence file: %s  sha256=%s\n' "$(basename "$file")" "$sha"
  local id="$EVIDENCE_PREFIX/$(basename "$file")"
  if [[ "$status" == PASS ]]; then
    set_var "${VARPREFIX}_STATUS" PASS; set_var "${VARPREFIX}_EVIDENCE_ID" "$id"
    echo; echo "=> ${VARPREFIX}_STATUS=PASS"; echo "=> ${VARPREFIX}_EVIDENCE_ID=$id"
    echo "   Local report and checksum created. This ID is not publishable until publish-variables.sh verifies it in R2."
  else
    echo; echo "NOT PASSING: fix the failures above and re-run. No variable values were written for ${VARPREFIX}." >&2
    return 1
  fi
}

# build_psql: sets PSQL=(...) from PSQL_CMD (e.g. "docker exec -i <container> psql -U lakhdatar -d lakhdatar") or DATABASE_URL.
# PSQL_CMD lets the kit run against a database that is only reachable inside Docker (such as an isolated recovery copy).
build_psql() {
  if [[ -n "${PSQL_CMD:-}" ]]; then read -r -a PSQL <<< "$PSQL_CMD"
  else : "${DATABASE_URL:?Set DATABASE_URL (read-only role) or PSQL_CMD}"; PSQL=(psql "${DATABASE_URL#jdbc:}"); fi
}
