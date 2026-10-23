# Payment chaos runbook (staging, provider sandbox only)

Run on https://staging-events.neelastack.com with Cashfree **sandbox** credentials. Never on production or real customer money.
Staging containers: `lakhdatar-staging-postgres`, `lakhdatar-staging-redis`, `lakhdatar-staging-backend`, `lakhdatar-staging-web`.
Use a disposable test event. Note the order number of every test order so you can look it up afterwards.

| # | Scenario | How |
|---|----------|-----|
| c1 | Browser closes after checkout creation | Start checkout, copy the order reference, close the tab before paying, reopen via `/recover` with order reference + email, then pay. Expect one order, one provider order. |
| c2 | Duplicate verify | Pay in sandbox, then replay the verify/return request twice (browser reload of the success page, or resend the request). Expect one confirmation, one set of tickets. |
| c3 | Webhook before browser verify | Pay in the sandbox and close the browser immediately; wait for the webhook to confirm the order. Open the ticket link afterwards. |
| c4 | Duplicate / out-of-order webhooks | In the Cashfree sandbox dashboard resend the same webhook several times. Expect no duplicate tickets, ledger rows or emails. |
| c5 | Provider down during refund | Cancel a paid test order. Temporarily block the backend's outbound access to the provider (or use an invalid sandbox secret), confirm the refund stays pending, restore access, wait for the recovery job (every few minutes). Expect one refund, no duplicates. |
| c6 | Refund fails then succeeds | Same as c5 but check the refund row went FAILED/PROCESSING and then COMPLETED, with exactly one `REFUND` ledger entry. |
| c7 | Cancel event during refund recovery | With several paid test orders, cancel the event while a refund is blocked; restore the provider; every order must end refunded or in manual review with a record. |
| c8 | Database restart mid-payment | Pay, and as soon as the provider shows success run `docker restart lakhdatar-staging-postgres`. Expect the order to reach a consistent final state after recovery. |
| c9 | Redis restart under load | Run checkout traffic, `docker restart lakhdatar-staging-redis`; checkout and scheduled jobs must continue afterwards. |

After the scenarios, wait 15-20 minutes for recovery jobs, then run `infra/readiness/payment-chaos.sh`. It checks the staging database for lost or created money and records your answers. Keep screenshots/notes of each scenario with the evidence.
