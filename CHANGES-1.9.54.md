# Changes — v1.9.54

## CI qualification and enterprise test hardening

- Replaced the brittle `TeamAuthContractTest` source-string assertion with behavior-level servlet-filter coverage for forced password changes.
- Added a test-only, ordered Razorpay gateway to `MultiEventCatalogIntegrationTest`; the integration test now exercises real publication readiness without depending on live payment credentials.
- Made the integration event fixture explicitly select Razorpay instead of relying on a domain default.
- Preserved the production requirement that the selected payment provider must be configured before an event can be published.
- Bumped all build/runtime version manifests from `1.9.53` to `1.9.54`.
- Added a release regression contract protecting the v1.9.54 test-isolation and version-consistency changes.

## Production behavior preserved

No production payment-provider requirement was weakened. The fake provider exists only in the Spring test context for the lifecycle integration test.
