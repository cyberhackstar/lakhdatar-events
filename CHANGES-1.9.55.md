# Changes — v1.9.55

## CI qualification correction

- Fixed the missing `JwtAuthFilter` import in `TeamAuthContractTest` that caused Maven `testCompile` to fail in the v1.9.54 CI run.
- Renamed the release contract to `ProductionHardeningV1_9_55ContractTest` and aligned its explicit version assertions with the release version.
- Preserved the behavioral forced-password-change coverage and test-only payment provider isolation introduced in v1.9.54.
- No production payment/refund business logic was changed by this corrective release.
- Active build/runtime manifests are synchronized to `1.9.55`.

## Enterprise release principle

The CI compiler failure is fixed at the test source level rather than weakening production controls. The existing production security and payment guards remain unchanged.
