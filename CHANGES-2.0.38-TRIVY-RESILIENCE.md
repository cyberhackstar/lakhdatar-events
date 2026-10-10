# Changes — v2.0.38 Trivy Scan Resilience

## Root cause addressed

The supplied 2026-10-10 Docker job log shows the backend Trivy image scan completed with zero HIGH/CRITICAL findings. The web-image scan then ran for about 311 seconds and failed with `context deadline exceeded`; the Trivy action used its five-minute default timeout. During setup, GitHub cache access and a fetch from the Trivy repository also encountered transient timeouts before setup recovered.

## Changes

- Set the Docker image qualification job timeout to 75 minutes to accommodate three architecture-tagged images and transient scanner setup/network delays.
- Install pinned Trivy once, then reuse that installed binary for all three image scans rather than rerunning Trivy installation/checkout for every image.
- Set explicit 15-minute timeouts for backend, web and edge scans.
- Keep `vuln,secret` scanners enabled, scan OS and library packages, and preserve the CRITICAL/HIGH failure gate. A timeout remains a failure; no scan error is treated as a pass.
- Preserve `.github/workflows/staging.yml` from the reference archive. The observed log is a Docker CI qualification failure that blocks successful downstream release qualification; it does not show a staging deploy attempt.
- Clarify that Cashfree/Razorpay provider API keys must not be put into `E2E_STAGING_PROVISIONING_CONFIG`. The deployment secret `STAGING_ENV_FILE` separately carries test/sandbox payment credentials, and the backend's preflight validates them.

## Unchanged

The application and deployment changes carried over from the v2.0.35 base remain in place; this release does not relax image scanning or production protections.
