# Neelastack Events v2.0.2 — CI Release Qualification Fixes

## Fixed

- Corrected the Operations Dashboard release contract false positive caused by the source comment containing the literal `payload` token. The implementation remains aggregate-only and does not expose webhook bodies or customer PII.
- Promoted application/runtime/default observability version metadata consistently to `2.0.2`.
- Updated the release contract to require the actual `2.0.2` manifests.
- Preserved the `monitor.neelastack.com` routing, Cloudflare Access guidance, host-only authentication cookie behavior, and private observability topology from v2.0.1.

## Scope

No payment, refund, reservation, inventory, ticket issuance, QR check-in, webhook processing, authentication flow, or public event API behavior was changed by this patch.
