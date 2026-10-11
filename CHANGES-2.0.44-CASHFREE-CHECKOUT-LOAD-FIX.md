# v2.0.44 — Cashfree checkout load-fixture repair

- Reused the v2.0.43 source as the baseline, preserving the passed staging/E2E configuration, single-owner HSTS directives, CSP nonce handling, and public-read/SEO load profiles.
- Corrected all three checkout load scripts (`checkout-idempotency.js`, `checkout.js`, and `checkout-hot-sale.js`) to send a synthetic 10-digit phone number and syntactically ordinary reserved example.com email address. The application accepts an omitted phone for other flows, but Cashfree's Create Order `customer_details.customer_phone` field is required by the provider API; an omitted field can turn checkout creation into a provider-side 5xx.
- Kept stable idempotency keys and payloads for duplicate requests and did not weaken the rate-limit or HTTP failure thresholds.
- Added safe idempotency failure diagnostics: unexpected status plus a whitelisted machine-readable error code only. Responses, payment-session data, customer details, and secrets are never logged.
- Extended the existing checkout target-guard CI test to require a 10-digit phone in all checkout load profiles and verify safe error-code diagnostics.
- Updated active release/runtime version metadata to 2.0.44. No backend business logic, database schema, frontend application logic, CSP/HSTS policy, payment-provider request flow, staging secrets, or E2E provisioning behavior was changed. Cashfree non-2xx logs now include only a whitelisted provider error code, not the provider response body.

## Validation boundary

Source-level guards, syntax checks, configuration checks and ZIP integrity are to be run on this exact package. The checkout fix still requires a live staging sandbox run because local CI cannot reproduce Cashfree's remote provider validation. Do not promote to production until backend/frontend CI, staging deploy, E2E, DAST, load qualification, and payment webhook/reconciliation gates pass on the same commit SHA.
