# Provider-order recovery fix (based on v2.0.38)

## Changes

- Limit missing-provider-order recovery to orders in `CREATED` or `AWAITING_PAYMENT`. An order whose reservation has expired and whose order status is `EXPIRED` is no longer selected on every worker sweep. The order/payment state is not modified by this query change.
- Pass eligible order statuses explicitly to `PaymentRepository.findMissingProviderOrderCandidates`.
- Add a regression contract that checks both the repository predicate and the service's allowed order statuses.
- Preserve the staging browser-E2E fixture correction: its event starts in 10 minutes, inside the scanner's configured early check-in window, instead of 30 days in the future.
- Avoid a needless Cashfree receipt GET before the first provider-order creation. The pre-creation state is retained in the request context; receipt lookup still happens when a previous attempt may have reached the provider but local persistence may have failed.
- Treat a 404 from an explicitly expected Cashfree receipt-recovery lookup as a debug-level lookup miss rather than a provider HTTP warning. Unexpected 404 responses for known provider resources remain warnings/errors.
- Add an offline HTTP-contract test covering the Cashfree v2025-01-01 order endpoint, idempotency header, expected missing-receipt response, and successful-payment payload mapping.

## Important scope note

These changes address two distinct misleading/retry behaviors observed in the supplied staging evidence: repeated recovery attempts for expired orders, and a predictable Cashfree receipt lookup before a first-time create request. The backend now keeps receipt lookup on ambiguous-retry paths so a lost create response can still be recovered safely.

Offline HTTP-contract tests do not replace a real Cashfree sandbox payment. Before production release, complete one real sandbox transaction and verify both the signed webhook path and the server-side `GET /orders/{order_id}/payments` reconciliation path. The successful browser qualification run alone does not establish that final payment settlement has been verified.
