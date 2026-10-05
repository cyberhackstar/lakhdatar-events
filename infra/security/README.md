# Staging DAST

Run `dast-staging.sh` against an isolated HTTPS staging environment. `ZAP_IMAGE` is deliberately required to use an immutable `@sha256:` image digest; record the chosen digest in CI/environment configuration rather than committing a mutable `latest` tag.

The baseline scan is unauthenticated. Authenticated DAST should be added with a staging-only ZAP context once the privileged E2E identities are provisioned.
