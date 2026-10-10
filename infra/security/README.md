# Staging DAST

Run `dast-staging.sh` against an isolated HTTPS staging environment. `ZAP_IMAGE` is deliberately required to use an immutable `@sha256:` image digest; record the chosen digest in CI/environment configuration rather than committing a mutable `latest` tag.

The baseline scan is unauthenticated. Authenticated DAST should be added with a staging-only ZAP context once the privileged E2E identities are provisioned.


## Staging ZAP baseline policy

`dast-staging.sh` loads `zap-baseline.conf`. Findings for duplicate HSTS and broad CSP sources remain
blocking warnings; expected cache policy, browser-app classification, immutable-asset cache hits,
public bundle timestamps, Angular query-reflection heuristic (covered by an E2E regression test), and
COEP omission for third-party payment iframe compatibility are explicitly triaged to INFO with reasons.
The HTML and JSON reports are still uploaded; do not replace this file with `-I` or a blanket wildcard ignore.
