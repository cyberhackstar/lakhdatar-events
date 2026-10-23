# Implementation status — v2.0.55

## Implemented in this candidate

- Bounded repair worker closes older `CREATED`/`AWAITING_PAYMENT` orders whose reservation records exist but none is live (already RELEASED/EXPIRED/CONFIRMED or elapsed); it clears the checkout session hash and deliberately does not modify the payment status.
- Event cancellation remains non-blocking with respect to the order repair worker; broad synchronous order updates were avoided to preserve lock ordering. Open orders with released reservation rows are repaired by the scheduled worker after cancellation commits.
- Payment reconciliation distinguishes the timestamp of the last attempt from a verified successful reconciliation (the historic database column name is preserved for schema compatibility).
- Adds a bounded 7–90 day historical lane for unresolved payments attached to `EXPIRED`/`CANCELLED` orders, separately capped from the current 7-day lane, with bounded recheck intervals and retry ordering.
- Provider-terminal/no-capture classification is fail-closed; local payment state only transitions after independent provider order terminal status, a non-empty list of known terminal non-captured attempts, amount/currency/receipt/reference checks and absence of any captured/refunded/unknown/in-flight attempt. An empty attempt list remains unresolved.
- Readiness scripts, runbook/checklists, and read-only R2 evidence inventory are bundled in `infra/readiness/`.
- Runtime reconciliation tuning uses safe repository defaults in `application.yml`; no new mandatory GitHub Environment variable is introduced.

## Validation run in packaging environment

- Input application ZIP integrity: PASS.
- Readiness kit ZIP integrity: PASS.
- `bash infra/readiness/test-readiness-kit.sh`: PASS.
- `bash -n` across readiness, certification, backup and deploy shell scripts: PASS.
- `bash infra/certification/test-private-repo-workflow-compat.sh`: PASS after version contract updated to 2.0.55.
- Full Maven compilation/JUnit, frontend npm CI/build, CI workflows, actual staging deployment, staging browser/payment tests, load test and DAST: **NOT RUN in this environment**. Maven is not installed, and this package job has no staging/payment provider credentials. The next authoritative validation is GitHub Actions for the exact pushed SHA.

## Staging finding that motivated this patch (2026-10-23)

The supplied staging diagnostics showed zero violations in all ten payment-integrity `FAIL` checks, but 2,128 stale payment warnings: 2,123 Cashfree PENDING records with provider order/session references but no provider payment reference; 788 of the orders were still AWAITING_PAYMENT even though their reservations were all RELEASED. Only one webhook record existed and it was already processed. These facts do not prove the provider never charged a payment. This candidate repairs stale order lifecycle state and queries Cashfree only for authoritative reconciliation; residual ambiguous records must stay visible and be reconciled with Cashfree sandbox evidence rather than manually changed.

## Release and evidence disposition

- Version: `2.0.55`; this is a new candidate and has not passed Enterprise Release Qualification.
- Deploy the exact candidate SHA to staging via GitHub Actions, then run Enterprise Release Qualification against the same SHA.
- The supplied Oct 21 qualification E2E log reports 21 passed and 6 skipped. The skips are expected because stateful mutation tests are deliberately Chromium-only; retain the test report for reviewers.
- Current readiness evidence remains incomplete for independent security assessment, payment-chaos scenarios, finance reconciliation, privacy/legal review, and privileged MFA review. The uploaded continuity record also says recovery scope is the existing VM with isolated network and excludes replacement-VM provisioning and DNS/Cloudflare Tunnel cutover. RPO/RTO must be tied to reviewed scope; WAL-availability latency is not by itself transaction-level RPO proof.
- Production decision: **HOLD**. Do not set `SINGLE_NODE_CERTIFIED_SHA`, do not change production data manually, and do not imply HA certification.
