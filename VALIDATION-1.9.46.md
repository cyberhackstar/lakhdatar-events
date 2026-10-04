# Validation — v1.9.46

- `EnterpriseScaleContractTest` uses whitespace-normalized checks for formatting-sensitive snippets.
- Production browser API base remains same-origin `/api/v1`; public backend port targeting remains prohibited.
- Cashfree hosted return/recovery contract remains enforced.
- Authenticated attendee CSV download path remains enforced.
- Shared Neelastack brand mark remains configured at 48px in primary headers.
- Supplied transparent Neelastack logo asset is packaged at `frontend/src/assets/neelastack-logo.png`.
- Package/version consistency and baseline verification are required before release.
- Full Maven/Angular GitHub Actions CI remains the authoritative final gate.
