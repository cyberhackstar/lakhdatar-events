# v2.0.38 — Staging E2E and CI Qualification Reliability

This candidate preserves the v2.0.35 tested application/deployment baseline and carries forward the v2.0.36–v2.0.37 staging E2E self-provisioning and security hardening. It adds resilience to the Docker image security-scanning gate after the supplied CI log showed Trivy's default five-minute scan timeout on the web image.

## What changed in v2.0.38

- Trivy is installed once per Docker qualification job and reused for backend, web, and edge image scans.
- Each image scan has an explicit 15-minute analysis timeout; the job still fails on CRITICAL/HIGH findings or an actual scan error.
- The Trivy vulnerability and secret scanners remain enabled. No severity gate or security scan is suppressed.
- Staging deployment workflow logic is preserved from the known-good v2.0.35 reference archive.
- The E2E provisioning secret remains minimal. Payment gateway API credentials belong only in the protected staging deployment `STAGING_ENV_FILE`, where the backend independently enforces sandbox/test mode.

## Release qualification status

This is a qualification candidate, not an automatic production certification. The reported CI failure was a Trivy scan timeout, not proof of an application vulnerability or a staging deployment error. Re-run GitHub Actions to confirm all three image scans and signed-image attestations complete. A real staging E2E run, cleanup verification, and existing release gates remain mandatory before production promotion.

See `CHANGES-2.0.38-TRIVY-RESILIENCE.md`, `VALIDATION-2.0.38-TRIVY-RESILIENCE.md`, `STAGING-QUALIFICATION-SETUP.md`, and `RELEASE-MANIFEST.txt`.
