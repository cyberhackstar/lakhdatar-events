# Neelastack Events 1.9.48

## Release hardening

- Removed the brittle, formatting-sensitive parsing from `EnterpriseScaleContractTest` that caused repeated CI failures when the payment-result component indentation or handler layout changed.
- Kept the contract focused on production behavior: a payment verification failure must remain recoverable, must not force `/recover`, must preserve an order/provider reference, and must use bounded retry.
- Preserved the same-origin production API routing, authenticated CSV download flow, Cashfree recovery behavior, and Neelastack logo/header changes from prior releases.
- Release metadata is consistently set to `1.9.48`.
