# v2.0.37 — E2E fixture security and mock-fidelity hardening

## Scope

This is a qualification-harness candidate based on the v2.0.35 tested application baseline. It does not claim production certification and does not alter production business flows; it adds a staging-only ADMIN payment-safety preflight endpoint and staging startup guard.

## Security and reliability changes

- Replaced fixed MFA seed literals and fixed mock-admin passwords with runtime-only cryptographically random fixtures. The mock remains loopback-bound and synthetic.
- Added an ADMIN-only, non-secret staging payment-safety endpoint and a provisioning preflight that checks the deployed backend mode before creating any resources. The backend refuses staging startup if configured Razorpay keys are not `rzp_test_`, Cashfree is not pointed at the exact sandbox endpoint, or the configured default provider is missing/inconsistent. The preflight endpoint confirms that the selected default and every configured provider are sandbox-safe.
- Changed the staging Compose Cashfree default to the sandbox endpoint.
- Restricted the multiline E2E provisioning config to an identity-only allowlist (environment marker, admin email/password/TOTP seed, organizer slug, provider name); rejects malformed lines and duplicate keys, and explicitly rejects payment gateway keys, database, JWT, SMTP, deployment, or other unapproved values.
- Masks E2E admin credentials and the TOTP seed individually in GitHub Actions logs; gateway keys are rejected by the E2E secret allowlist and remain confined to the staging deployment environment.
- Removed response-body snippets from provisioning errors to avoid inadvertently logging user-controlled or sensitive response content; reports HTTP status and a constrained API error code only.
- Retry policy prevents all POST retries, even if a caller marks an operation retry-safe; a refreshed admin token is not used to replay potentially mutating POSTs after HTTP 401. Only reads and explicitly idempotent DELETE/PUT/PATCH operations may be retried.
- Cleanup can be invoked again after a partial failure, allowing retryable unassign/deactivate/cancel operations to complete. Teardown then verifies through the API that the event is CANCELLED, generated users are inactive, and assignments are gone; cleanup failures continue to fail qualification. The harness cannot guarantee cleanup after a forcibly killed runner, so an orphan audit remains required.
- Corrected the in-process mock ticket model so each ticket carries its actual order position and order count; public ticket views and scan responses now return these values based on fixture data rather than hard-coded `2 of 2`.
- Added self-test coverage for dynamic MFA, config allowlisting, duplicate/malformed config, accurate ticket positions, and transient cleanup retry.
- Updated version metadata to 2.0.37; application behavior otherwise remains based on the existing tested baseline.

## Operational security

- Use only a protected `staging` environment and a dedicated, least-privilege staging administrator. Keep MFA enabled and use the actual TOTP seed only in `E2E_STAGING_PROVISIONING_CONFIG`.
- Use Cashfree sandbox credentials with `https://sandbox.cashfree.com/pg` or a complete Razorpay `rzp_test_` set. Never provide live payment credentials.
- Do not use `STAGING_ENV_FILE` as the E2E secret. The allowlist intentionally rejects unrelated runtime secrets.
- Full staging mutations should be manually dispatched and reviewed; scheduled runs should remain read-only.
