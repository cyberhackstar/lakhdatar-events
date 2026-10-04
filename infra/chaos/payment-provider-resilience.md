# Payment-provider resilience / chaos test plan

Run these scenarios only against a provider test/sandbox account and a dedicated staging event. The expected result is never “create another order blindly”; the expected result is a deterministic state transition that can be reconciled.

| Scenario | Expected behavior |
|---|---|
| Provider connect timeout while creating order | Local payment remains recoverable; no duplicate provider order is assumed |
| HTTP 502 after provider accepted request | Reconciliation lookup runs before creating another order |
| Provider order created but response body lost | Receipt lookup discovers and adopts the existing provider order |
| Provider definitively returns order not found | Safe order creation retry is allowed |
| Duplicate webhook delivery | Idempotent; one financial/ticket transition |
| Out-of-order webhook | State machine rejects illegal backwards transition |
| Webhook processing crashes mid-flight | Recovery job picks it up and reprocesses |
| Capture succeeds, customer returns twice | Ticket issuance remains idempotent |
| Refund response times out | Refund remains recoverable; no second blind refund |
| Provider becomes unavailable for 10 minutes | Recovery backlog grows but business records remain durable; operations dashboard shows the backlog |

For every scenario verify database state, provider state, order number/receipt, payment reference, reservation state, ticket count, ledger rows, webhook event state and audit log state.
