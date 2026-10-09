# Validation — Neelastack Events v2.0.35 Local Qualification Fix 2

## Corrective validation

- The Windows `mvn -B -ntp clean verify` run reached 239 backend tests and reported exactly one failure in `ScheduledMutationTransactionContractTest`.
- The reported failure was a false negative in the test's fixed 220-character source look-back, not a missing production annotation.
- `PaymentWebhookEventRepository.deleteTerminalOlderThan` contains `@Transactional` immediately before its multiline `@Modifying` / `@Query` declaration.
- The qualification test now scopes its search to the target repository method by checking the last `@Transactional` annotation after the previous method terminator.

## Runtime fixes retained

- Exact slashless `/api/v1/public/events` NGINX route.
- Public catalog regression qualification.
- Visibility-safe scanner E2E locator.
- Payment/recovery and structured logging hotfixes from v2.0.35 runtime hotfix 1/2.
- Ticket access, QR credential and order-ticket-count behavior.

## Windows validation to perform

```powershell
mvn -B -ntp clean verify
```

Expected: BUILD SUCCESS with 239+ tests and 0 failures/errors.

Then validate the local runtime:

```powershell
docker compose up -d --build backend web edge
docker exec lakhdatar-dev-edge nginx -t
curl.exe -i "http://127.0.0.1:4002/api/v1/public/events?page=0&size=12"
```

The catalog request must return `HTTP/1.1 200` without a 301/302 redirect.
