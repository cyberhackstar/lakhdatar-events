# v2.0.45 validation record

## Root cause addressed

The load log counted edge-level NGINX request-limit rejections as 503 because the default NGINX status was in use. The public API rate and burst limits are unchanged; their rejection status is now 429. Application/provider 503s continue to be counted by the existing `checkout_unavailable_503` threshold.

The post-run database invariant checker now gives the scheduled reservation-expiry worker a bounded 28-second opportunity to clear newly expired HELD reservations. It retries only `EXPIRED_HELD_RESERVATIONS_REMAIN`; all other invariant failures remain fail-fast.

## Required checks

- `bash infra/loadtest/test-checkout-target-guard.sh`
- `bash infra/smoke/test-hsts-ownership.sh`
- `node tools/verify-platform-baseline.mjs`
- `node tools/verify-frontend-lock.mjs`
- `node tools/verify-security-policy.mjs`
- Shell/JavaScript syntax checks and all workflow YAML parses.
- NGINX edge and HA syntax checks.
- ZIP integrity and active version alignment.

## Not claimed

This validation note does not claim a live load-test or post-deploy pass. Rerun CI, staging deployment, E2E, DAST, load qualification and provider sandbox/webhook checks on the same commit SHA.
