# Neelastack Events v2.0.4 — Enterprise Logging & Diagnostics

## Production diagnostics

- Added structured ECS-style operational logging across authentication, orders/payments, provider calls, reservations/inventory, check-in, webhooks, refunds, mail, media and recovery jobs.
- Added request lifecycle completion logs with method, route, HTTP status, outcome and duration. Successful fast requests stay at DEBUG; slow/4xx requests are WARN and 5xx requests are ERROR.
- Added application lifecycle `ready`/`stopping` events for restart/deployment diagnosis.
- Added a defensive logging facade that redacts credentials, tokens, secrets, signatures, cookies, authorization material, customer/attendee contact details, request/response bodies and arbitrary DTO/object values; long strings are bounded.
- Added an ADMIN-only Operations Center Live Logs panel backed by private Loki.
- Added service, level, time-window and search filters, pause/resume, manual refresh and bounded 5-second polling.
- Log responses expose only a small allow-list of safe diagnostic fields and preserve correlation IDs for edge -> API -> business-event investigation.
- Log panel failure is isolated from the primary Operations dashboard.

## Reliability and production behavior

- Preserved the v2.0.3 monitor routing correction so the public monitor URL never leaks the internal `:8080` origin port.
- Integrated the corrected Angular dependency lockfile supplied during the deployment investigation; dependency entries match the supplied lockfile and the invalid `void-elements-2.0.2` / `http-errors-2.0.2` tarball references remain absent.
- Preserved high-concurrency NGINX/Tomcat capacity tuning, keepalive pools, bounded public catalogue caching and connection/rate protections.
- Added bounded Docker JSON-file log rotation to the production and HA application examples.
- Disabled Micrometer OTLP metrics push by default because Prometheus already scrapes the backend metrics endpoint; this prevents a missing local metrics collector from creating recurring connection-refused noise.
- Kept tracing separate from the business path so observability failure does not become an application availability dependency.
- Fixed the logging-only AuthService brace regression before release qualification so the existing conditional login rate limiter remains conditional.

## Scope boundary

No payment state-machine, inventory allocation, ticket issuance, QR validation, refund semantics, webhook signature verification or public API business behavior was intentionally changed for the logging feature. The only business-code correction in this release is restoration of the pre-existing login rate-limit conditional that was accidentally widened during logging instrumentation.
