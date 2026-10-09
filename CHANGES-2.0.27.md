# Changes — v2.0.27

## Production reliability hardening

- Fixed Spring Boot structured ECS logging collisions when a throwable is logged together with custom `error.*` fields. Throwable-path custom error fields are now namespaced as `failure.*`, while the ECS `error` object remains owned by the exception cause.
- Preserved Operations Center compatibility with both legacy `error.*` fields and the new `failure.*` namespace.
- Fixed PostgreSQL webhook stale-processing recovery by explicitly casting the retry timestamp to `timestamptz`; this removes the runtime `timestamp with time zone` vs text CASE expression failure.
- Hardened ticket PDF QR embedding by using PDFBox content-detected byte-array image creation for the server-generated PNG instead of an AWT/ImageIO conversion path.
- Ticket PDF generation now fails closed when the QR payload is missing/invalid rather than silently producing a ticket without the gate QR.
- Reused PDFBox Standard-14 font instances per document to reduce repeated object construction.
- Added a real ticket PDF regression test that generates and reopens the PDF with PDFBox, including non-ASCII attendee data to exercise the production sanitization boundary.
- Added structured-logging and webhook SQL contract regressions.

## Release posture

v2.0.27 is a release candidate, not a self-certified production deployment. Runtime certification still requires the repository's CI, staging, load, DAST, payment-chaos, backup/restore and HA gates to execute against the target infrastructure.
