#!/usr/bin/env bash
# Privileged MFA enforcement + recovery evidence.
#   ENV_FILE=/path/to/staging.env PSQL_CMD='docker exec -i lakhdatar-staging-postgres psql -U readiness_readonly -d lakhdatar' \
#   BASE_URL=https://staging-events.neelastack.com OPERATOR='Name' \
#   bash infra/readiness/mfa-verification.sh
# Secrets are never printed: the env file is only tested for presence/shape.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; source "$SCRIPT_DIR/lib.sh"
build_psql
: "${BASE_URL:?BASE_URL (public site URL) is required}"; : "${ENV_FILE:?ENV_FILE (staging env file path before deployment; production env file only after schema migration) is required}"
HOST="$(python3 - "$BASE_URL" <<'PYHOST'
import sys, urllib.parse
print((urllib.parse.urlsplit(sys.argv[1]).hostname or '').lower())
PYHOST
)"
if [[ "$HOST" != staging-events.neelastack.com && "${CONFIRM_NONSTAGING_MFA_TARGET:-no}" != yes ]]; then
  echo "Refusing MFA check against '$HOST'. Use staging-events.neelastack.com before deployment, or explicitly confirm an approved post-deploy target." >&2; exit 2
fi
begin_report mfa-verification MFA "Privileged MFA enforcement and recovery"
envval() { grep -E "^$1=" "$ENV_FILE" | tail -1 | cut -d= -f2- | tr -d '"' || true; }
[[ -f "$ENV_FILE" ]] || { fail "env file not found"; finish_report; exit 1; }
[[ "$(envval MFA_REQUIRED_FOR_PRIVILEGED)" == "true" ]] && pass "MFA_REQUIRED_FOR_PRIVILEGED=true" || fail "MFA_REQUIRED_FOR_PRIVILEGED is not true in the env file"
K="$(envval MFA_ENCRYPTION_KEY)"; (( ${#K} >= 32 )) && pass "MFA_ENCRYPTION_KEY is set (length >= 32; value not recorded)" || fail "MFA_ENCRYPTION_KEY missing or shorter than 32 characters"
q() { "${PSQL[@]}" -X -Atqc "$1"; }
MFA_COLS="$(q "select count(*) from information_schema.columns where table_schema='public' and table_name='users' and column_name in ('mfa_enabled','mfa_secret_enc')")"
if [[ "$MFA_COLS" != 2 ]]; then
  fail "database schema lacks MFA columns (expected V34-V41-capable schema); do not run this against pre-migration V32 production"
else
  PRIV="'ADMIN','ORGANIZER','EVENT_MANAGER','FINANCE'"
  TOTAL="$(q "select count(*) from users where enabled and role in ($PRIV)")"; say "Enabled privileged users: $TOTAL"
  UNENR="$(q "select count(*) from users where enabled and role in ($PRIV) and (not mfa_enabled or mfa_secret_enc is null)")"
  [[ "$UNENR" == 0 ]] && pass "every enabled privileged user has MFA enrolled" || fail "$UNENR enabled privileged user(s) have no MFA enrolled (enrol them or disable the accounts)"
  SHORT="$(q "select count(*) from users where mfa_enabled and length(coalesce(mfa_secret_enc,'')) < 40")"
  [[ "$SHORT" == 0 ]] && pass "stored MFA secrets have the shape of encrypted values" || fail "$SHORT MFA secret(s) look unencrypted"
  ADM="$(q "select count(*) from users where enabled and role='ADMIN' and mfa_enabled")"
  (( ADM >= 2 )) && pass "$ADM enabled admins with MFA (a second admin can reset a locked-out one)" || fail "only $ADM admin with MFA; a lost authenticator could lock everyone out. Create a second admin with MFA"
fi
for path in /api/v1/admin/dashboard /api/v1/admin/security/users/00000000-0000-0000-0000-000000000000/mfa/reset; do
  code="$(curl -s -o /dev/null -w '%{http_code}' -X "$([[ $path == *reset ]] && echo POST || echo GET)" --max-time 20 "$BASE_URL$path" || echo 000)"
  [[ "$code" == 401 || "$code" == 403 ]] && pass "$path rejects anonymous access ($code)" || fail "$path returned $code to an anonymous request"
done
say ""; say "== Reviewer checklist (recovery drill must really have been performed) =="
checklist "$SCRIPT_DIR/checklists/mfa.txt"
finish_report
