# Validation v2.0.12

## Source/static gates
- Webhook stale-claim recovery is provider-scoped for both providers.
- Privileged MFA reset revokes active sessions.
- Production privileged MFA is fail-closed.
- Stateful E2E retry is disabled for destructive/idempotent business mutations.
- Enterprise evidence gate is protected-environment and fail-closed.
- Production and HA promotion workflows verify signed images and GitHub provenance.
- HA node deployment and rollback scripts are syntax-checked.
- Security policy, Dependabot, CODEOWNERS, and governance files are present.
- Temporary build/source artifacts are excluded from the release tree.

## Runtime certification gates
- Fresh PostgreSQL and V31-to-latest Flyway qualification is now a mandatory CI job; its evidence is retained with the release.
These must execute in the actual CI/staging environment before `ENTERPRISE_CERTIFIED_SHA` is set:
- Backend `mvn -B -ntp clean verify`
- Angular production install/build/unit tests
- Playwright desktop/Android/iOS enterprise E2E
- Fresh and upgraded Flyway migration qualification
- 1,000+ user mixed load and check-in load
- Razorpay/Cashfree sandbox chaos and reconciliation
- Immutable-image DAST
- HA failover across separate failure domains
- PostgreSQL PITR restore with measured RPO/RTO
- Independent penetration/security review
- Real Alertmanager paging test
- Financial reconciliation sign-off
- Privacy/data-governance sign-off

The certification workflow now refuses to produce a certification manifest unless those evidence identifiers and PASS attestations exist in the protected certification environment.

## Final source-level result
All available local static/release-gate checks pass in this audit environment. Full runtime certification remains intentionally fail-closed and must be established by the protected CI/staging workflows using real infrastructure evidence; no synthetic PASS record has been added to the release tree.
