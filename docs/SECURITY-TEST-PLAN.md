# Enterprise security qualification

The release process should combine SAST/SCA/container security (already in CI) with staging DAST and an independent penetration test.

## Required test families

- Authentication/session: password reset, refresh rotation, replay/reuse detection, MFA enrollment/challenge/reset.
- Authorization: IDOR/tenant isolation across organizer/event/ticket/finance surfaces.
- Business logic: duplicate checkout, inventory races, duplicate webhook/provider callbacks, refund races, QR replay.
- Input/output: XSS, SSRF, SQLi, upload validation, parser abuse, header/host attacks.
- Availability: rate-limit bypass, webhook storms, oversized requests, expensive PDF workloads.
- Secrets/data exposure: logs, trace attributes, error responses, source maps, build artifacts.

Record scope, date, tested release SHA, findings, severity, remediation and retest evidence.
