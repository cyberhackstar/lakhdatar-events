# Neelastack Event Platform 2.0.39 — Qualification fixes

This consolidated release combines the payment provider-order recovery and Cashfree initialization
changes from the 2.0.38 working bundle with fixes for the three qualification failures reported on
2026-10-10.

## Included fixes

- Corrected the two release-contract failures from the 2026-10-10 CI run: the normalized Java source assertion now matches its whitespace-free string, and `RELEASE-MANIFEST.txt` now reports release 2.0.39 and references the matching change/validation records.
- Carried forward the v2.0.38 recovery fix: missing-provider-order recovery selects only `CREATED` and `AWAITING_PAYMENT` orders, preventing repeated retries against expired/cancelled orders.
- Carried forward the Cashfree first-creation fix: new `NOT_CREATED` payment sessions create directly, while ambiguous retries still look up the stable order receipt before attempting another create.
- Flyway qualification now reads the last successful non-null migration by `installed_rank`, not
  `MAX(version)`. The prior SQL text comparison reported `9` even though the fresh and upgrade test
  databases had both applied migration 41.
- ZAP DAST creates an absolute output mount, makes that temporary directory writable to the
  unprivileged ZAP container, passes report basenames rather than absolute `/zap/wrk/...` names, and
  restores the original directory permissions after the scan. This addresses the reported permission
  failure and doubled report path.
- Enterprise load qualification validates all required variables at once, records a redacted
  preflight artifact, and provides the exact configuration guide instead of stopping at the first
  empty `EVENT_ID`.
- Version metadata is bumped consistently to 2.0.39 and the frontend dependency doctor now derives
  the expected version from `../VERSION`.

## Important status boundaries

- The load-test job will remain blocked until the `LOADTEST_*` fixture secrets are configured in the
  GitHub Actions `staging` environment. A source patch cannot invent a staging database URL, event,
  valid ticket credentials or unexpired staff/admin tokens.
- The older ZAP scan recorded 0 fail-level alerts, 8 warning categories and 59 passive-rule passes.
  Its job failed while producing the report. The warnings (including duplicate HSTS entries,
  broad HTTPS image sources/CSP wildcard, cache directives, a potential XSS reflection, timestamp
  disclosure and COEP) still need review from the newly generated report; this fix does not falsely
  mark them remediated.
- A real sandbox checkout, webhook, and server-side payment reconciliation have not been performed by
  this patching environment. Validate these in staging before production rollout.

## Validation performed for this bundle

Static source and metadata checks plus Bash syntax checks are run when generating the archive. Maven,
Docker-based Flyway qualification, ZAP and k6 cannot run in the current patching workspace, so their
runtime results must come from the project's GitHub Actions runners/staging environment.
