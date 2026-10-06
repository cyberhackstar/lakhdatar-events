# Validation v2.0.20

- VERSION: 2.0.20
- Bash syntax: PASS
- Release contract coverage: added for HA/PITR live-evidence requirements
- ZIP/package integrity: verified during release packaging
- Backend Maven: must be executed by CI
- Frontend production build: must be executed by CI
- k6 load: requires protected staging
- Cashfree/Razorpay sandbox E2E: requires provider credentials
- HA failover/PITR restore: requires production-equivalent infrastructure

This release deliberately does not represent infrastructure evidence as locally passed.
