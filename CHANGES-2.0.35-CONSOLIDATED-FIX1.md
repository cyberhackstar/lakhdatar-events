# Neelastack Event Platform 2.0.35 — Consolidated Fix 1

## Root-cause fix

The local backend qualification exposed one failing contract test:
`ScheduledMutationTransactionContractTest.allScheduledCleanupMutationsDeclareTransactions`.

The test reported a missing `@Transactional` annotation on
`PaymentWebhookEventRepository.deleteTerminalOlderThan(...)`.

Inspection confirmed the repository method already declares `@Transactional`.
The failure was caused by the contract test inspecting only the 220 characters immediately
before the method name. The multiline native SQL query between the annotation and method
exceeded that fixed window.

The contract test now associates `@Transactional` with the target method by locating the
previous method declaration terminator (`;`) and requiring the last `@Transactional` before
the target method to occur after that boundary. This preserves the contract without modifying
business behavior or weakening transaction requirements.

## Local qualification status

- Frontend dependency bootstrap: PASS
- Frontend dependency verification: PASS
- Frontend runtime dependency audit: PASS (0 HIGH/CRITICAL runtime vulnerabilities)
- Frontend production build: PASS
- Frontend unit tests: PASS (12/12)
- Backend Maven qualification on the prior candidate: 238/239 tests passed; 1 contract-test false negative
- This release contains the corrected contract test. Backend `mvn clean verify` must be rerun locally as Gate 3.
