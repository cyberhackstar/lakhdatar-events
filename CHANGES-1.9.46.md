# Changes — v1.9.46

## CI reliability and Neelastack branding

- Fixed the `EnterpriseScaleContractTest` false negative caused by a source-comment line wrap; contract assertions now normalize whitespace while preserving the same behavioral requirements.
- Retained the production browser same-origin API guard, Cashfree return/recovery behavior, authenticated CSV export, and checkout protections.
- Replaced the platform Neelastack mark with the supplied high-resolution transparent horse + wordmark asset.
- Cropped only transparent outer whitespace from the supplied artwork so its explicit 48px header height renders at the intended visual scale.
- Increased the shared logo max-width allowance while retaining a 48px maximum rendered height to prevent clipping without changing header layout.
- Kept the premium checkout, login, recovery, payment-result, admin and event-operation UI hardening from v1.9.45.

## Validation scope

This release contains a CI contract-test correction and branding asset update. Payment, inventory, authentication, authorization, event lifecycle and financial invariants are unchanged.
