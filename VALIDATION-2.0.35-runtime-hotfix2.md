# Validation — v2.0.35 Runtime Hotfix 2

## Static checks

- EnterpriseLog provider-key collision guard: PASS
- Operations log provider.name/legacy provider fallback: PASS
- Regression test source updated: PASS
- Java syntax/stub compilation of changed production/test classes: PASS

## Runtime qualification

The target Windows environment must run the following before reusing the hotfix in staging/production:

```text
mvn -B -ntp -Dtest=EnterpriseStructuredLoggingContractTest,EnterpriseLoggingContractTest test
mvn -B -ntp clean verify
Docker rebuild/restart
POST /api/v1/public/checkout with the existing E2E idempotency key
Verify no "Duplicate nested pairs added under 'provider'" appears in backend logs
Verify the actual Razorpay HTTP status/error is now observable if provider configuration is still invalid
```

This package was not falsely marked as having a Maven test pass in the packaging environment because Maven is not installed there.
