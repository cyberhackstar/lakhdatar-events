# Security Policy

## Supported releases

Only the current production release and the immediately preceding production release receive routine security fixes.

## Vulnerability reporting

Do not disclose suspected vulnerabilities in public issues. Report them privately through the repository security contact configured by the repository owner. Include reproduction steps, affected component, impact, and sanitized request/response material.

## Production baseline

Production releases require secret scanning, SAST/CodeQL, dependency and container scanning, DAST, privileged MFA, immutable release images, artifact provenance, signed container images, authorization/tenant-isolation tests, and an incident-response path.

Never include payment credentials, JWTs, refresh tokens, MFA secrets, password-reset tokens, private keys, production `.env` files, database dumps, or customer data in source control or build artifacts.
