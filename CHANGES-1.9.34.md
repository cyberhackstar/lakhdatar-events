# v1.9.34 — Payment, scanner and admin-query reliability correction

## Production fixes

- Cashfree Web Checkout now handles the provider Promise/result, including success (`paymentDetails`), provider error, redirect, close and rejected-promise paths.
- Cashfree/Razorpay SDK loading is deterministic for both newly injected and already-present script tags, with a bounded loader timeout and safe recovery messaging.
- Cashfree opens in the provider modal target and server-side verification remains authoritative through the existing checkout verification endpoint.
- Ticket scanner keeps the camera session alive between scans and immediately shows a `Verifying ticket…` state while `/checkin/scan` runs; the live camera is only stopped for lifecycle cases such as logout, offline, tab visibility changes or manual entry.
- Admin/operations/finance query parameters are centrally sanitized so optional `undefined`, `null` and blank filters are never sent to the backend.
- Cloudflare Web Analytics beacon host is allowed by the edge Content-Security-Policy without broadening payment provider origins.
- Added Angular HTTP contract tests covering omission and preservation of optional admin query parameters.
- Added release-baseline guards for Cashfree result handling, SDK timeout/recovery, scanner camera reuse, query-param sanitization and Cloudflare CSP.

## Compatibility

No backend API contracts, Flyway migrations, database schemas, payment verification rules, ticket credentials or check-in authorization logic were changed in this release.
