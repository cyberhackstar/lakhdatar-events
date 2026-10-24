#!/usr/bin/env bash
# One-time: create /home/ubuntu/apps/lakhdatar-events/.env with generated secrets (mode 0600).
# Usage on the production VM:  bash bootstrap-production-env.sh [--force]
# It asks only for values nobody can generate for you. Secrets are never printed.
set -Eeuo pipefail
APP_DIR="${APP_DIR:-/home/ubuntu/apps/lakhdatar-events}"
ENV_FILE="$APP_DIR/.env"
TEMPLATE="${TEMPLATE:-$APP_DIR/.env.example}"
mkdir -p "$APP_DIR"
[[ -f "$TEMPLATE" ]] || { echo "Template not found: $TEMPLATE (copy .env.example from the repo there first)" >&2; exit 2; }
if [[ -f "$ENV_FILE" && "${1:-}" != "--force" ]]; then
  echo "$ENV_FILE already exists. Re-running would rotate secrets and break the existing database/sessions. Use --force only for a brand-new install." >&2; exit 2
fi

hex()  { openssl rand -hex 32; }
pw()   { openssl rand -base64 48 | tr -d '=+/\n' | cut -c1-40; }
ask()  { local p="$1" v; read -r -p "$p: " v; printf '%s' "$v"; }
asks() { local p="$1" v; read -r -s -p "$p: " v; echo >&2; printf '%s' "$v"; }
OWNER="$(ask 'GitHub owner/org that holds the images (lowercase, e.g. cyberhackstar)')"
[[ "$OWNER" =~ ^[a-z0-9-]+$ ]] || { echo "Owner must be lowercase letters/digits/dashes" >&2; exit 2; }
SUPPORT_EMAIL="$(ask 'Real, monitored support email')"
[[ "$SUPPORT_EMAIL" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ && "${SUPPORT_EMAIL,,}" != *example.com* && "${SUPPORT_EMAIL,,}" != *@yourdomain.com ]] || { echo "Invalid/placeholder support email" >&2; exit 2; }
SUPPORT_PHONE="$(ask 'Real public support phone (8-15 digits, e.g. +919876543210)')"
digits="${SUPPORT_PHONE//[^0-9]/}"
(( ${#digits} >= 8 && ${#digits} <= 15 )) && [[ ! "$digits" =~ (.)\1{7,} && "$digits" != *00000* && "$SUPPORT_PHONE" =~ ^[-+0-9()\ .]+$ ]] || { echo "Invalid/placeholder support phone" >&2; exit 2; }
CF_ID="$(asks 'Cashfree LIVE App ID (hidden)')"
CF_SECRET="$(asks 'Cashfree LIVE Secret Key (hidden)')"
[[ -n "$CF_ID" && -n "$CF_SECRET" ]] || { echo "Cashfree live credentials are required" >&2; exit 2; }
[[ "$CF_ID" != TEST* && "$CF_SECRET" != cfsk_ma_test* ]] || { echo "Those look like Cashfree TEST credentials; production needs LIVE keys" >&2; exit 2; }

umask 077
tmp="$(mktemp "$APP_DIR/.env.XXXXXX")"
cp "$TEMPLATE" "$tmp"
setv() { # setv KEY VALUE  -> replace the first KEY= line (value written verbatim, '|' and '&' safe via awk)
  KEY="$1" VAL="$2" awk 'BEGIN{k=ENVIRON["KEY"];v=ENVIRON["VAL"];d=0} index($0,k"=")==1 && !d {print k"="v; d=1; next} {print} END{if(!d) print k"="v}' "$tmp" > "$tmp.n" && mv "$tmp.n" "$tmp"
}
setv IMAGE_NAMESPACE "ghcr.io/$OWNER"
setv ALLOW_SINGLE_NODE_PRODUCTION true
setv DB_PASSWORD "$(pw)"
setv REDIS_PASSWORD "$(pw)"
setv JWT_SECRET "$(hex)"
setv TICKET_VIEW_SECRET "$(hex)"
setv QR_SIGNING_SECRET "$(hex)"
setv MFA_ENCRYPTION_KEY "$(hex)"
setv SUPPORT_EMAIL "$SUPPORT_EMAIL"
setv SUPPORT_PHONE "$SUPPORT_PHONE"
setv CASHFREE_APP_ID "$CF_ID"
setv CASHFREE_SECRET_KEY "$CF_SECRET"
setv DEFAULT_PAYMENT_PROVIDER CASHFREE
setv BOOTSTRAP_ENABLED false
setv INITIAL_ADMIN_SETUP_ENABLED false
chmod 600 "$tmp"; mv "$tmp" "$ENV_FILE"

# Self-check against the application's production guard rules (no secret values are printed).
bad=0
chk() { local k="$1" min="$2" v; v="$(grep -E "^$k=" "$ENV_FILE" | head -1 | cut -d= -f2-)"; (( ${#v} >= min )) || { echo "FAIL $k shorter than $min" >&2; bad=1; }; }
chk DB_PASSWORD 12; chk REDIS_PASSWORD 32; chk JWT_SECRET 32; chk TICKET_VIEW_SECRET 32; chk QR_SIGNING_SECRET 32; chk MFA_ENCRYPTION_KEY 64
[[ "$(stat -c %a "$ENV_FILE")" == 600 ]] || { echo "FAIL .env mode is not 600" >&2; bad=1; }
(( bad == 0 )) || exit 1
echo "Created $ENV_FILE (mode 600). Secrets were generated on this VM and not displayed."
echo "Still to do by hand: put R2 credentials in ~/.aws/credentials profile 'neelastack-r2', and back up this .env somewhere safe (losing MFA_ENCRYPTION_KEY locks every admin out of MFA)."
echo "Mail is left disabled (optional). To enable it, fill ALL MAIL_* fields in the file."
