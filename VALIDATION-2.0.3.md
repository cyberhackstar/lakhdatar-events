# v2.0.3 Validation Record

## Source fixes applied

- Release version: `2.0.3`.
- Frontend lockfile: replaced from the newer project-supplied lockfile and checked as JSON. `void-elements` resolves to `2.0.1`; `http-errors` resolves to `2.0.1`; stale `2.0.2` tarball URLs for those packages are absent.
- Monitor root: absolute canonical HTTPS redirect to `/admin/operations`, never the internal `:8080` port.
- Edge: upstream keepalive pools, bounded public-read cache, gzip, and 1,000-connection per-client ceiling.
- Backend: Tomcat max connections 10,000; accept queue 1,000; max request threads 200; keep-alive 30s.
- SSR: keep-alive 60s, headers 65s, request timeout 30s.
- Containers: `nofile` soft/hard 65,536.
- Metrics: Micrometer OTLP metrics export disabled by default; Prometheus scrape endpoint remains enabled.
- Added controlled 1,000-VU public-read load profile for staging qualification, with a CI guard that blocks the live `events.neelastack.com` hostname.

## Static validation performed

- CI load-test workflow YAML parse: PASS.
- The 1,000-VU workflow profile is explicitly blocked from the live `events.neelastack.com` hostname.

- ZIP extraction/integrity: PASS.
- JSON parsing: PASS for package metadata and package lock.
- YAML parsing: PASS for production/HA/Cloudflare configuration.
- Shell syntax: PASS for deployment/load-test shell scripts.
- NGINX syntax: validated in the appropriate `events/http` wrapper context.
- Release metadata consistency: PASS for active 2.0.3 manifests.
- `tools/verify-platform-baseline.mjs`: PASS.
- `npm ci --dry-run --offline --ignore-scripts --no-audit --no-fund`: PASS; lockfile remained unchanged and 658 packages were resolved from the lock.
- Direct NGINX monitor-root behavior test: PASS; `Host: monitor.neelastack.com` returns `Location: https://monitor.neelastack.com/admin/operations`.
- Secret-material filename scan: PASS; no `.env`, private-key, keystore, or certificate files are shipped in the package.

## Required authoritative validation

The repository CI job must still run:

```text
mvn -B -ntp clean verify
```

and the frontend production build/audit plus Docker image build/health regression tests. A full 1,000-concurrent-user qualification must be run against staging/a dedicated load-test event; this environment cannot honestly certify that benchmark without executing it against the deployed topology.
