# v2.0.49 — Align backend certification contract test with the separated workflows

## Root cause

The v2.0.48 backend verification ran 245 tests and reported exactly one failure: `EnterpriseReleaseV212ContractTest.enterpriseCertificationGateIsFailClosed`, line 41. The assertion still expected `.github/workflows/enterprise-release-qualification.yml` to contain `environment: enterprise-certification` and expected that same workflow to call `verify-enterprise-evidence.sh`.

That expectation belonged to the old combined workflow. v2.0.48 intentionally moved protected operational-evidence checks to `.github/workflows/enterprise-production-certification.yml`, while release qualification produces a non-production-certification manifest. The implementation and separate workflow regression test already reflected this architecture; one Java contract test was stale.

## Changes

- Updated the backend contract test to assert that release qualification does not require the protected production-certification environment.
- Asserted that the separate Enterprise Production Certification workflow does require the protected environment, downloads and verifies the matching qualification artifact, and invokes the operational evidence validator.
- Kept the evidence validator's fail-closed assertions for HA evidence ID, HA PASS status, and production topology.
- Bumped active release coordinates to 2.0.49 without changing historical release records.

## Security boundary

No test is disabled or skipped. Production and HA deployment gates remain protected by `ENTERPRISE_CERTIFIED_SHA`; no evidence is fabricated and no certification requirements are weakened.
