# Validation — v2.0.27

## Source-level validation completed

- Archive unpacked and source modified from v2.0.26 local-E2E candidate.
- JSON/YAML structure checks and shell syntax checks can be run from the repository.
- Added regression coverage for the exact three release blockers: structured ECS throwable logging, webhook retry timestamp typing, and ticket PDF generation.

## Required runtime gates before production

1. `mvn -B -ntp clean verify` in the backend.
2. Angular SSR production `npm ci && npm run build` and frontend tests.
3. Local/CI Chromium E2E with a disposable issued test ticket; the PDF test must return HTTP 200 and a parseable PDF.
4. Staging deployment of the exact candidate SHA.
5. Staging enterprise qualification: Flyway fresh/upgrade, backend checks, frontend checks, browser E2E, k6 load gate and DAST.
6. Enterprise evidence artifact creation and review.
7. Production promotion only from the exact certified SHA with protected secrets and the existing fail-closed deployment checks.

## Important

This document deliberately does not claim these runtime gates passed merely because source/static validation succeeded. Production certification must be based on executed target-environment evidence.
