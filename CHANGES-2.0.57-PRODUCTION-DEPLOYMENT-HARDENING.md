# v2.0.57 — production deployment and configuration hardening

- Move the single-node production migration-sensitive rollback boundary before backend Compose startup so partial starts cannot trigger an unsafe image-only rollback; block new deploys while a failed attempt is unresolved and preserve its marker if the recovery attempt itself fails.
- Move production public smoke checks to step-level environment inputs and remove unnecessary ticket bearer/ID GitHub secrets from the public smoke workflow. Remote smoke still reads optional synthetic credentials from the server's protected `.env`.
- Transfer only the exact tracked Git archive and refuse packaging a tracked runtime `.env` file; never ship the runner workspace or its temporary SSH material.
- Replace manual `SINGLE_NODE_CERTIFIED_SHA` matching with fail-closed validation of the successful, recent, exact-SHA single-node readiness artifact. Protected production environment approval and explicit single-node break-glass acknowledgement remain required.
- Remove routine single-node topology/node-count GitHub variables; the workflow is intrinsically single-node and deployment scripts still refuse the enterprise-HA configuration.
- Make staging runtime configuration updates explicit opt-in. Normal staging deployments reuse the VM's owner-protected `.env`, so `STAGING_ENV_FILE` is no longer a routine deployment prerequisite.
- Fix the discrepancy between optional SMTP documentation/tests and the production configuration guard: no SMTP fields disables outbound mail, while partial SMTP configuration fails closed.
- Make tracing/Loki exporters opt-in unless the standalone observability stack is configured, avoiding silent connections to unavailable `tempo`/`loki` containers by default.
- Add unit-testable GitHub readiness-artifact validation, including exact SHA, topology, protected-environment attestation, all nine evidence identifiers, and freshness.

This patch does not assert that external systems (GitHub, Cashfree, SMTP, Cloudflare R2, DNS/Cloudflare Tunnel, or the production VM) are configured or that a real release qualification has passed. The exact candidate must pass fresh CI and staging qualification before deployment.
