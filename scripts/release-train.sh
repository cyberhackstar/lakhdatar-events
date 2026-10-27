#!/usr/bin/env bash
# One-command release train for lakhdatar-events.
#
#   CI-green SHA -> Staging Deploy -> Enterprise Release Qualification
#                -> Single-Node Production Readiness -> (optional) Production
#
# It only dispatches and waits for the existing, unchanged workflows and passes the SHA and the
# qualification run ID between them. It never bypasses a gate: every workflow still performs its own
# verification, GitHub Environment approvals still apply, and Production still needs the explicit
# SINGLE-NODE-BREAK-GLASS confirmation and the production environment reviewer.
#
# Run it as yourself (gh auth login) so the deploy workflows see a normal GitHub user as github.actor.
#
# Usage:
#   scripts/release-train.sh [--sha <40-hex>] [--from staging|qualification|readiness|production]
#                            [--qualification-run <id>] [--production --confirm SINGLE-NODE-BREAK-GLASS]
#                            [--dry-run]
set -Eeuo pipefail

REPO="${REPO:-cyberhackstar/lakhdatar-events}"
REF="main"
WAIT_MINUTES="${WAIT_MINUTES:-180}"
POLL_SECONDS="${POLL_SECONDS:-30}"
SHA=""
FROM="staging"
QUAL_RUN=""
PRODUCTION=false
CONFIRM=""
DRY_RUN=false

die() { echo "ERROR: $*" >&2; exit 1; }
log() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*"; }

while (($#)); do
  case "$1" in
    --sha) SHA="${2:-}"; shift 2 ;;
    --from) FROM="${2:-}"; shift 2 ;;
    --qualification-run) QUAL_RUN="${2:-}"; shift 2 ;;
    --production) PRODUCTION=true; shift ;;
    --confirm) CONFIRM="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN=true; shift ;;
    -h|--help) sed -n 2,19p "$0"; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

command -v gh >/dev/null || die "GitHub CLI (gh) is required"
command -v jq >/dev/null || die "jq is required"
case "$FROM" in staging|qualification|readiness|production) ;; *) die "--from must be staging|qualification|readiness|production" ;; esac
if [[ "$PRODUCTION" == true && "$CONFIRM" != "SINGLE-NODE-BREAK-GLASS" ]]; then
  die "--production requires --confirm SINGLE-NODE-BREAK-GLASS (explicit single-node risk acceptance)"
fi
if [[ "$FROM" == production && "$PRODUCTION" != true ]]; then die "--from production requires --production"; fi

resolve_sha() {
  # Only a push-to-main CI run that fully succeeded has signed, scanned images for its SHA.
  # Docs-only commits skip CI (paths-ignore), so the newest main commit may have no images.
  gh api "repos/$REPO/actions/workflows/ci.yml/runs?branch=$REF&event=push&status=success&per_page=1" \
    --jq '.workflow_runs[0].head_sha // empty'
}

if [[ -z "$SHA" ]]; then SHA="$(resolve_sha)"; fi
[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || die "could not resolve a CI-green release SHA; pass --sha"
MAIN_HEAD="$(gh api "repos/$REPO/commits/$REF" --jq .sha)"
log "Release SHA:      $SHA"
log "main HEAD:        $MAIN_HEAD"
[[ "$SHA" == "$MAIN_HEAD" ]] || log "NOTE: main is ahead of the release SHA (newer commits have no CI-built images yet, or are docs-only)."
status="$(gh api "repos/$REPO/compare/$SHA...$REF" --jq .status 2>/dev/null || echo unknown)"
[[ "$status" == ahead || "$status" == identical ]] || die "release SHA is not an ancestor of $REF (compare status: $status)"

if [[ "$DRY_RUN" == true ]]; then
  log "Dry run: would run stages from '$FROM' for $SHA (production=$PRODUCTION). Nothing dispatched."
  exit 0
fi

dispatch() { # <workflow-file> [-f key=value ...] -> prints run id
  local workflow="$1"; shift
  local started out id
  started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  out="$(gh workflow run "$workflow" -R "$REPO" --ref "$REF" "$@" 2>&1)" || die "dispatch of $workflow failed: $out"
  id="$(grep -oE '/actions/runs/[0-9]+' <<<"$out" | head -n1 | grep -oE '[0-9]+' || true)"
  if [[ -z "$id" ]]; then
    for _ in $(seq 1 20); do
      sleep 5
      id="$(gh run list -R "$REPO" --workflow "$workflow" --event workflow_dispatch --branch "$REF" --limit 5 \
            --json databaseId,createdAt --jq "[.[]|select(.createdAt >= \"$started\")]|sort_by(.createdAt)|last|.databaseId // empty")"
      [[ -n "$id" ]] && break
    done
  fi
  [[ -n "$id" ]] || die "could not determine the run ID for $workflow"
  echo "$id"
}

wait_run() { # <run-id> <label>
  local id="$1" label="$2" deadline last="" st concl
  deadline=$(( $(date +%s) + WAIT_MINUTES * 60 ))
  log "$label: https://github.com/$REPO/actions/runs/$id"
  while :; do
    read -r st concl < <(gh run view "$id" -R "$REPO" --json status,conclusion --jq '"\(.status) \(.conclusion)"')
    [[ "$st" == "$last" ]] || { log "$label: $st"; last="$st"; }
    if [[ "$st" == completed ]]; then
      if [[ "$concl" == success ]]; then return 0; fi
      echo "--- failed jobs/steps ---" >&2
      gh run view "$id" -R "$REPO" --json jobs \
        --jq '.jobs[]|select(.conclusion=="failure")|"\(.name): " + ([.steps[]|select(.conclusion=="failure")|.name]|join(", "))' >&2 || true
      die "$label finished with conclusion '$concl'. Open the run above, fix the cause, then resume with --from."
    fi
    (( $(date +%s) < deadline )) || die "$label did not finish within ${WAIT_MINUTES} minutes (approval pending?)"
    sleep "$POLL_SECONDS"
  done
}

stage_order=(staging qualification readiness production)
should_run() { # <stage>
  local want="$1" s
  for s in "${stage_order[@]}"; do
    [[ "$s" == "$FROM" ]] && break
    [[ "$s" == "$want" ]] && return 1
  done
  return 0
}

if should_run staging; then
  id="$(dispatch staging.yml -f "release_sha=$SHA" -f configure_runtime=false)"
  wait_run "$id" "Staging Deploy"
fi

if should_run qualification; then
  id="$(dispatch enterprise-release-qualification.yml -f "release_sha=$SHA" -f staging_url=https://staging-events.neelastack.com)"
  QUAL_RUN="$id"
  wait_run "$id" "Enterprise Release Qualification"
fi

if should_run readiness; then
  [[ "$QUAL_RUN" =~ ^[0-9]+$ ]] || die "readiness needs the qualification run ID; pass --qualification-run <id>"
  id="$(dispatch single-node-production-readiness.yml -f "release_sha=$SHA" -f "qualification_run_id=$QUAL_RUN")"
  wait_run "$id" "Single-Node Production Readiness"
fi

if [[ "$PRODUCTION" == true ]]; then
  log "Dispatching Production deploy. Approve the 'production' environment in GitHub when prompted."
  id="$(dispatch production.yml -f operation=deploy -f "release_sha=$SHA" -f "confirm_single_node=$CONFIRM")"
  wait_run "$id" "Production"
  log "Production deploy of $SHA completed."
else
  log "Release $SHA is qualified and ready. Re-run with --from production --production --confirm SINGLE-NODE-BREAK-GLASS --sha $SHA to deploy."
fi
