# Payment chaos runbook (staging, provider sandbox only)

Run on https://staging-events.neelastack.com with Cashfree **sandbox** credentials. Never on production or real customer money.
Staging containers: `lakhdatar-staging-postgres`, `lakhdatar-staging-redis`, `lakhdatar-staging-backend`, `lakhdatar-staging-web`.
Use a disposable test event. Note the order number of every test order so you can look it up afterwards.

| # | Scenario | How |
|---|----------|-----|
| c1 | Browser closes after checkout creation | Start a Cashfree sandbox checkout and copy its `LK-...` order number. Close the tab before paying. Open `/recover`, enter the order number and the same checkout email, confirm the pending order, then choose **Continue payment for this order**. Complete the sandbox payment. Verify the same order/provider order is reused and exactly one ticket set is issued. Do not create a second checkout. |
| c2 | Duplicate verify | Pay in sandbox, then replay the verify/return request twice (browser reload of the success page, or resend the request). Expect one confirmation, one set of tickets. |
| c3 | Webhook before browser verify | Pay in the sandbox and close the browser immediately; wait for the webhook to confirm the order. Open the ticket link afterwards. |
| c4 | Duplicate / out-of-order webhooks | In the Cashfree sandbox dashboard resend the same webhook several times. Expect no duplicate tickets, ledger rows or emails. |
| c5 | Provider down during refund | Cancel a paid test order. Temporarily block the backend's outbound access to the provider (or use an invalid sandbox secret), confirm the refund stays pending, restore access, wait for the recovery job (every few minutes). Expect one refund, no duplicates. |
| c6 | Refund fails then succeeds | Same as c5 but check the refund row went FAILED/PROCESSING and then COMPLETED, with exactly one `REFUND` ledger entry. |
| c7 | Cancel event during refund recovery | With several paid test orders, cancel the event while a refund is blocked; restore the provider; every order must end refunded or in manual review with a record. |
| c8 | Database restart mid-payment | Pay, and as soon as the provider shows success run `docker restart lakhdatar-staging-postgres`. Expect the order to reach a consistent final state after recovery. |
| c9 | Redis restart under load | Run checkout traffic, `docker restart lakhdatar-staging-redis`; checkout and scheduled jobs must continue afterwards. |


**C1 safety gate:** the recovery endpoint must match the canonical `LK-...` order number and original checkout email, accept only an unpaid `CREATED`/`AWAITING_PAYMENT` order with a live reservation and eligible payment status, reuse the existing provider order, and rotate the secure `ld_checkout` HttpOnly cookie. Expired/cancelled/confirmed orders must not resume payment. If the reservation has expired or any invariant fails, record C1 as failed; do not create a replacement order to force a pass.

After the scenarios, wait 15-20 minutes for recovery jobs, then run `infra/readiness/payment-chaos.sh`. It checks the staging database for lost or created money and records your answers. Keep screenshots/notes of each scenario with the evidence.


## Guarded Windows runner

After the runner change is merged to main and the local repository is updated, run the no-mutation preflight first from the repository root:

    .\infra\readiness\run-payment-chaos-staging.ps1 -PreflightOnly

Only if the preflight passes, schedule a quiet staging window and run the guided exercise:

    .\infra\readiness\run-payment-chaos-staging.ps1

The runner verifies the fixed staging HTTPS origin, the release marker and matching successful release-qualification artifact, the staging Compose project/container labels, staging-only database and Redis volumes, service health, and the deployed Cashfree sandbox configuration. The full run guides the operator through C1–C9, records actual observed outcomes, waits for recovery jobs, calls the existing read-only financial-invariant/evidence script, and can upload the bundle to R2 only after a separate explicit confirmation. It does not publish GitHub Environment variables.

**Automation boundary:** C1–C4 and C5–C7 still need real sandbox/provider actions or an already reviewed fault-injection method; the runner does not fake them or introduce unreviewed provider-specific API calls. C8/C9 restart only the named staging PostgreSQL or Redis container, and each restart requires a literal interactive confirmation plus staging release/container/volume checks and a recovery-readiness check. Run the restart tests only in an approved maintenance window. Use the preflight-only option for diagnostics; it performs no service restart and does not create payment orders.

The runner stores evidence locally under %LOCALAPPDATA%\Neelastack\payment-chaos-evidence. Keep credentials, bearer tokens, MFA seeds, customer details, and payment information out of screenshots and notes. R2 upload is opt-in and immutable-key only. **It is not a production-readiness certification workflow** and never updates protected GitHub readiness variables.
