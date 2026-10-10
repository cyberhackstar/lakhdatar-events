# Validation — provider-order recovery fix

Based on the supplied v2.0.38 source ZIP.

## Change

The missing-provider-order recovery query now joins each payment to its order and only selects orders whose status is `CREATED` or `AWAITING_PAYMENT`. Payments attached to `EXPIRED`, `CANCELLED`, `CONFIRMED`, or other non-payable order states are not recovery candidates. This is a query-selection fix; it does not mutate payment/order records or change reservation-expiry validation.

The regression contract covers the query predicate and the eligible order-status list. The staging E2E fixture event is also scheduled 10 minutes in the future, preserving the correction used by the successful staging-browser qualification.

## Checks completed in the available workspace

- Passed: patch applies to the original v2.0.38 source with `git apply --check`.
- Passed: `git diff --check` after applying the patch.
- Passed: Node syntax validation of `e2e/support/provision.js`.
- Passed: static source assertions for the order join, order-status predicate, repository parameter, allowed service statuses, regression-test presence, and 10-minute E2E fixture timing.
- Passed: archive integrity check (`unzip -t`) when packaged.

## Checks not completed here

- `mvn -B -ntp -f backend/pom.xml clean verify`: not run because Maven is not installed in this workspace; installing it timed out.
- `npm --prefix e2e run selftest`: not run because the dependency installation timed out. Run it on the development machine, then run the Maven verification command above.
- No staging/production deployment or payment-provider transaction was performed.

## Cashfree follow-up in the same v2.0.38 release

The supplied staging trace shows the HTTP 404 was a `GET /pg/orders/{order_id}` receipt lookup before a fresh order was created. Fresh `NOT_CREATED` payments now skip that redundant lookup; lookup is retained for ambiguous retry recovery. Expected missing receipts during recovery are logged at debug level, while 404 responses for known provider resources remain warnings.

The offline Cashfree HTTP-contract test is present but has not been executed in this workspace because Maven/dependencies are unavailable. This is not a claim that a real payment succeeded. Before production, execute a real Cashfree sandbox transaction and verify both webhook-based confirmation and server-side order-payment reconciliation.
