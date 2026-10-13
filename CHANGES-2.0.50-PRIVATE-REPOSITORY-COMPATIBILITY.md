# v2.0.50 private-repository workflow compatibility

- Guard GitHub artifact attestation creation/verification so it runs only for public repositories, where the current GitHub plan supports it.
- Keep BuildKit provenance and SBOM generation enabled for all three OCI images.
- Require keyless Cosign signing in CI and verify the CI workflow identity before promotion. Staging, single-node production, and HA workflows always verify Cosign signatures; GitHub artifact-attestation checks are an additional public-repository-only gate.
- Keep CodeQL analysis enabled for private repositories while retaining SARIF reports as a workflow artifact when GitHub Code Scanning uploads are unavailable.
- Update source-contract/unit tests so future changes cannot accidentally reintroduce unconditional GitHub-attestation API calls.

This is workflow/deployment compatibility hardening, not a replacement for Single-Node Production Readiness, measured RPO/RTO, restore/PITR evidence, or reviewer approval.

- The safe production dotenv template now includes the actual non-secret R2 bucket/endpoint and named AWS profile settings; deploy scripts align `APP_VERSION` and OpenTelemetry `service.version` to the checked-out release `VERSION`.
