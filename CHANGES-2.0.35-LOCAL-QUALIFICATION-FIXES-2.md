# Neelastack Events v2.0.35 — Local Qualification Fix 2

## Fixed after Windows clean-build qualification

- Corrected `ScheduledMutationTransactionContractTest` so it identifies `@Transactional` by repository-method scope instead of a fragile fixed 220-character look-back window.
- The `PaymentWebhookEventRepository.deleteTerminalOlderThan` production method already declares `@Transactional`; the previous qualification test falsely reported it missing because its multiline native `@Query` placed the method signature more than 220 characters after the annotation.
- No payment-flow, authentication, database-schema, or runtime transaction semantics were weakened or changed.

## Preserved

- Exact slashless `/api/v1/public/events` edge routing.
- Scanner Playwright strict-mode/visibility fix.
- Runtime hotfix 1 transaction/recovery hardening.
- Runtime hotfix 2 structured logging provider collision guard.
- Ticket access/QR protections and ticket/order count fields.
