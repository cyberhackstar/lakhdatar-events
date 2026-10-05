# v2.0.4 Validation Record

## Release

- Version: `2.0.4`
- Base package: v2.0.3 production-ready release.
- Primary addition: enterprise structured operational logging across the backend plus a secure ADMIN-only Loki-backed Live Logs viewer in the Operations Center.
- The supplied updated Angular `package-lock.json` dependency tree was integrated; all dependency records match the supplied lockfile, while the release root metadata is correctly promoted to `2.0.4`.

## Structured logging qualification

- Defensive logging facade added with ECS-compatible structured fields.
- Sensitive field redaction covers passwords, tokens, secrets, authorization headers, cookies, signatures, credentials, customer/attendee contact fields, and request/response bodies.
- Arbitrary DTO/object values are refused by the logging facade rather than serialized into operator logs.
- String values are bounded to 512 characters.
- Request completion logging emits action, method, route, status, duration and outcome; normal successful requests remain DEBUG to avoid excessive production log volume, while 4xx/slow requests are WARN and 5xx are ERROR.
- Structured operational events cover authentication, order/payment flow, provider calls, reservations/inventory, check-in, webhooks, refunds, mail, media and recovery jobs.
- Application lifecycle emits explicit ready/stopping markers.
- No direct `log.info/debug/warn/error/trace(...)` calls remain in backend production Java; operational logging is routed through `EnterpriseLog`.

## Operations Center log viewer qualification

- `GET /api/v1/admin/ops/logs` is ADMIN-only and responds with `Cache-Control: no-store`.
- Loki access is private over the application/observability network; Prometheus/Grafana/Loki/Tempo are not publicly published.
- Query window is bounded to 24 hours and result size is bounded to 200 entries.
- The server parses ECS JSON and plain/container log formats and returns only an allow-list of safe diagnostic fields.
- Level filtering is performed after parsing so it works for structured backend logs and plain edge/container logs.
- The UI provides service, level, time-window and text filters, pause/resume, manual refresh and 5-second polling only while the log panel is open.
- The log panel remains independently accessible when the dashboard snapshot itself fails.
- Correlation IDs are displayed so an operator can connect edge -> API -> business event -> recovery activity.
- Loki failure degrades only the log panel and does not make the main Operations Center unavailable.

## Dependency lockfile qualification

- `frontend/package-lock.json` parses as lockfile v3.
- `node_modules/void-elements` is pinned to `2.0.1` with the valid `void-elements-2.0.1.tgz` tarball.
- `node_modules/http-errors` is pinned to `2.0.1` with the valid `http-errors-2.0.1.tgz` tarball.
- No `void-elements-2.0.2.tgz` or `http-errors-2.0.2.tgz` references exist.
- All 658 non-root dependency records exactly match the supplied updated lockfile.
- `npm ci --dry-run --offline --ignore-scripts --no-audit --no-fund` passed and reported 658 packages.
- A real networked `npm ci` was attempted but exceeded the tool transport timeout; no installed dependencies were retained in the release workspace.

## Production infrastructure qualification

- NGINX configuration fragment validated successfully using a valid `events/http` wrapper with container DNS replaced only for local syntax testing.
- Production/HA/observability/Cloudflare/application YAML parsed successfully.
- Deployment shell scripts pass `bash -n`.
- Docker `json-file` logs are bounded with `10m` max-size and `5` files in the production and HA examples to prevent unbounded local disk growth.
- Existing NGINX keepalive, public catalogue micro-cache, connection/rate protections and 10,000 Tomcat connection / 1,000 accept queue capacity remain preserved.
- Default Micrometer OTLP metrics push is disabled; Prometheus remains the metrics source, preventing a missing local OTLP metrics receiver from generating recurring metric push errors.

## Source-integrity qualification

- Java delimiter-balance scan: PASS across backend production sources.
- No secret files (`.env`, private key, PEM, PKCS#12/JKS, SSH key) are packaged.
- No `node_modules`, Angular build output, Maven `target` or other generated build directories are packaged.
- ZIP integrity test passes after final packaging.

## Important release gate

The assembly environment does not contain Maven and cannot run the authoritative backend `mvn -B -ntp clean verify` suite. The official CI pipeline must remain the final compile/test gate, followed by frontend production build, image build/push, deployment smoke tests and a staged k6 concurrency test before a production capacity claim is made.

## Post-deploy verification

1. All application containers report release `2.0.4`.
2. `https://monitor.neelastack.com/admin/operations` loads without an internal `:8080` URL.
3. ADMIN login succeeds and `/api/v1/admin/ops/dashboard` returns `200`.
4. `/api/v1/admin/ops/logs` returns structured entries and correlation IDs.
5. Non-ADMIN access to the log endpoint remains denied.
6. Loki/Grafana/Prometheus/Tempo are not directly reachable from the public internet.
7. Payment, refund, reservation and webhook recovery queues are inspected before any manual intervention.
8. Run the staged 1,000-VU k6 scenario and review p95 latency, error-rate and host/database headroom before increasing traffic targets.
