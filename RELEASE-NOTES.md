# v2.0.57 — Production deployment hardening (candidate; not certified)

- Harden the single-node migration failure marker/rollback selection and transfer only the tracked exact-SHA Git archive to the VM; runner SSH files and temporary policy checkout are excluded.
- Require release promotion workflows to dispatch from trusted `main`, verify Cosign signatures using the exact CI identity, and use qualification/readiness provenance bound to the exact candidate SHA.
- Automatically validate the matching successful single-node readiness artifact, including evidence IDs and freshness, instead of requiring manual certified-SHA variables.
- Reduce routine GitHub Environment configuration to three deployment SSH secrets per environment; staging runtime `.env` upload is opt-in. Operational readiness evidence remains deliberately explicit and cannot be fabricated.
- Align SMTP/monitoring defaults, validate the selected payment provider and required support contacts, pass runtime placeholders through Compose, and align the HA example with the production configuration guard.
- Add regression tests for workflow provenance, deploy archive/rollback safety, legal-content placeholders, readiness artifacts, qualification-run provenance, and platform contracts.
- **Not production-certified:** the recorded prior load qualification failed; this ZIP has not passed fresh full CI/live staging qualification, the owner-confirmed legal/contact/refund fields remain TODOs, and production operational evidence requires real drills and reviews.

---

# v2.0.56 — Checkout load and reconciliation isolation

## Qualification harness-only correction (no application version change)

- The latest v2.0.56 enterprise run passed application, database, browser and DAST gates; its load job failed on one HTTP status-0 connection reset to the Cloudflare edge, not a checkout HTTP 5xx.
- The checkout probe retries that single no-response case once with the exact same idempotency key; it does not retry received HTTP 4xx/5xx responses. Final logical response, server-error, latency and database-invariant thresholds remain strict.
- The qualification workflow uses the immutable workflow-dispatch SHA as its test-harness revision and records `testHarnessSha` separately from the deployed application `releaseSha`, allowing the existing staged release to be re-qualified without application redeployment.
- This does not grant production certification. Rerun same-SHA enterprise qualification and pass the separate single-node readiness gate before any production deployment.

- Restores oldest-attempt-first retry ordering in the normal and historical reconciliation queries.
- Runs historical closed-order reconciliation in a separately scheduled lower-rate lane under the shared distributed lock, limiting its competition with current checkout/provider requests.
- Moves legacy stale-open-order repair out of the 15-second reservation-expiry sweep to a 60-second lane capped at 100 orders per pass, sharing the expiry lock to avoid cross-node overlap.
- Adds sanitized HTTP status, allowlisted API error code and response duration to a bounded number of checkout load-test failure messages.
- Leaves enterprise load thresholds unchanged. v2.0.55's p95 8.33-second checkout latency is consistent with the 8-second Cashfree read timeout, but the provider-side cause is not confirmed without the missing sanitized response code.
- Candidate only: rerun CI, staging deployment, mutation-enabled E2E and enterprise release qualification. Production remains HOLD.

# v2.0.55 — Stale payment and reservation lifecycle hardening

- Closes old open checkout orders when the bounded reservation worker confirms they have no live reservation; checkout-session hashes are cleared. This does not alter the payment state.
- Adds a bounded 7–90 day backlog lane for old payments attached to already-closed orders; normal and backlog batches are separately capped and fairly rotated.
- A payment can transition to local FAILED/CANCELLED only when the provider order is independently confirmed terminal, provider payment attempts are all terminal non-captured states (or empty), order identity/amount/currency checks pass, and no captured/refunded/unknown/in-flight evidence exists.
- Provider/network errors, missing provider resources, active orders, unknown statuses and any captured/refunded attempt remain unresolved and do not clear the warning.
- Includes the v2.0.57 hardened readiness kit and read-only evidence-inventory PowerShell helper. CI/staging/production gates and secrets are not bypassed.
- This package has not yet run Maven/JUnit or GitHub Actions for its new SHA. Treat it as a release candidate only.

# v2.0.54 — Cashfree expiry root-cause fix

- Staging candidate; production gates unchanged. See `CHANGES-2.0.54-CASHFREE-EXPIRY-ROOT-CAUSE.md`.

# v2.0.53 — Cashfree order-expiry compatibility (superseded by 2.0.54)

- Keep the local ticket reservation and Cashfree order expiry aligned while increasing the default hold to 20 minutes to provide provider-validation headroom.
- Serialize `order_expiry_time` at whole-second UTC precision (`...Z`) instead of passing the nanosecond precision emitted by a live `Instant`.
- Extend the Cashfree HTTP contract test to cover an expiry Instant with fractional nanoseconds.
- Explicitly set `RESERVATION_HOLD=20m` in the staging example; deployment environment overrides must be reviewed because they supersede Compose defaults.
- This is a staging candidate only. The provider's `order_expiry_time_invalid` response must be cleared by a real sandbox checkout before production promotion.

# v2.0.52 — Cashfree phone normalization and E2E diagnostics

- Normalize common Indian phone formats to Cashfree's 10-digit `customer_phone` contract.
- Reject malformed phone values before making a payment-provider request.
- Add Cashfree HTTP-contract regression tests for country-code normalization and invalid input.
- Restore the CI check-in latency SLO contract test removed from the v2.0.51 workflow.
- Upload both Playwright HTML reports and `test-results` traces for failed staging E2E runs.
- Keep the HTML reporter enabled in release qualification; do not override it with line-only reporting.
- Staging E2E and payment verification must pass before production promotion; this release does not certify production readiness.

# v2.0.51 — Cashfree order expiry alignment

- Passes reservation expiry when provisioning a new Cashfree payment order.
- Preserves compatibility with the existing payment-provider method contract.
- Adds an HTTP contract regression test for `order_expiry_time`.
- Does not modify or reconcile previously created provider orders.
- Production approval remains subject to the release qualification and operational readiness gates.
# v2.0.50 — Single-node production readiness

- Adds a separate protected Single-Node Production Readiness workflow for the current one-VM topology, with same-SHA release qualification verification and fail-closed operational evidence.
- Requires measured backup/recovery RPO/RTO, off-host backup, restore/PITR, recovery drill, security, payment recovery, alerting, finance reconciliation, privacy and MFA evidence.
- Produces a distinct single-node readiness manifest that explicitly does not grant enterprise HA certification.
- Single-node production promotion requires `SINGLE_NODE_CERTIFIED_SHA`, exact SHA matching, `PRODUCTION_TOPOLOGY=single-node`, `HA_NODE_COUNT=1`, explicit risk acknowledgement, signed image/provenance and smoke checks. Enterprise HA gates remain unchanged.

# v2.0.48 — Release qualification and enterprise certification gate separation

- Separates source/backend/frontend/database/browser/load/DAST qualification from the external operational evidence required for enterprise production certification.
- Produces a release-qualification artifact bound to the exact SHA and workflow run ID, explicitly marked as not granting production certification.
- Adds a separate protected certification workflow that verifies the matching successful qualification artifact before checking real HA, PITR, penetration test, payment chaos, alerting, finance, privacy and MFA evidence.
- Aggregates missing/invalid certification values in a single actionable error and preserves the protected `ENTERPRISE_CERTIFIED_SHA` production gate.

# v2.0.26 — Clean-schema certification fix

- Adds V41 to normalize authentication hash columns from fixed-width CHAR(64) to VARCHAR(64), matching JPA and eliminating whitespace-padding schema validation failures.
- Keeps existing V34/V40 migrations immutable; the change is forward-only and safe for upgraded databases.

## Historical — v2.0.22 — Certification Candidate

V33 Flyway syntax and release qualification regressions fixed; no payment/API behavior intentionally changed.

## Historical — Neelastack Events v2.0.21

Security & Financial Certification Hardening release.

This release is deliberately conservative. It preserves v2.0.22 behavior and fixes one concrete compile-time regression in the payment provider guard. The fix ensures configured bulkhead acquisition timeout and circuit-open duration are actually applied, while focused regression contracts protect the wiring.
