#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../frontend"
npm install --package-lock-only --ignore-scripts --no-fund
npm ci --ignore-scripts --no-fund
npm audit --audit-level=high
echo 'Frontend dependency lock remediation completed.'
