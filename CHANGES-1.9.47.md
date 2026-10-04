# Neelastack Events v1.9.47

## CI reliability fix
- Removed the brittle EnterpriseScaleContractTest assertion that depended on a human-readable comment in the payment-result component.
- The contract test now verifies the actual runtime recovery behavior: transient verification failures stay pending, preserve an order/provider reference, retry with a bounded attempt count, and never force navigation to `/recover`.
- This keeps the production behavior protected without making release qualification depend on comment wording.

## Inherited production hardening
- Same-origin production API routing.
- Cashfree return/reconciliation handling.
- Authenticated CSV Blob downloads.
- Premium form/input styling and validation.
- 48px Neelastack shared branding.
- Multi-day booking window, payment idempotency and recovery safeguards.
