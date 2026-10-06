# v2.0.26 — Clean-schema certification fix

- Adds V41 to normalize authentication hash columns from fixed-width CHAR(64) to VARCHAR(64), matching JPA and eliminating whitespace-padding schema validation failures.
- Keeps existing V34/V40 migrations immutable; the change is forward-only and safe for upgraded databases.

## Historical — v2.0.22 — Certification Candidate

V33 Flyway syntax and release qualification regressions fixed; no payment/API behavior intentionally changed.

## Historical — Neelastack Events v2.0.21

Security & Financial Certification Hardening release.

This release is deliberately conservative. It preserves v2.0.22 behavior and fixes one concrete compile-time regression in the payment provider guard. The fix ensures configured bulkhead acquisition timeout and circuit-open duration are actually applied, while focused regression contracts protect the wiring.
