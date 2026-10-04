#!/usr/bin/env bash
set -euo pipefail
: "${DATABASE_URL:?DATABASE_URL is required}"
: "${LOADTEST_EVENT_ID:?LOADTEST_EVENT_ID is required}"
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v event_public_id="$LOADTEST_EVENT_ID" -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/verify-invariants.sql"
