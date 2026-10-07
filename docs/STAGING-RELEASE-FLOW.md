# Staging release flow

Use this sequence after `CI` is green:

1. Run `Staging Deploy` with the exact 40-character SHA from the green `CI` run.
2. Confirm `https://staging-events.neelastack.com` is serving the SHA and passes staging smoke checks.
3. Run `enterprise-e2e-staging` with `run_mutations=false` for routine read-only browser coverage. Use `run_mutations=true` only with fresh disposable checkout/check-in data.
4. Run `Staging Load Test` against the same staging URL. Never enable checkout load with production payment credentials.
5. Run `Enterprise Release Qualification` using the same 40-character release SHA and `https://staging-events.neelastack.com`.
6. Promote only the exact certified SHA through the production workflow or HA promotion workflow.

Production remains isolated at `events.neelastack.com` and loopback port `4002`.
