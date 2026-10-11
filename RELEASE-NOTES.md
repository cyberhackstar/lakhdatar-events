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
