# Changes — v2.0.28

## Ticket PDF production reliability fix

- Fixed the ticket PDF runtime failure under PDFBox 3.x caused by passing 0–255 RGB values into the float-based color API, which requires normalized 0.0–1.0 components.
- Centralized PDF RGB normalization and bounds validation so future color changes cannot silently reintroduce the same runtime defect.
- Kept QR embedding on PDFBox's byte-array PNG path, avoiding unnecessary AWT/ImageIO conversion in the production PDF renderer.
- Retained fail-closed behavior: a ticket PDF is not produced when the server-generated QR image is missing or invalid.
- Preserved the v2.0.27 structured logging collision fix and provider-scoped webhook retry timestamp cast.
- Existing ticket PDF regression now exercises the exact production rendering path and will fail the build if this PDFBox color contract is broken again.

## Release posture

v2.0.28 is a release candidate. It must pass the full CI build, frontend build/tests, browser E2E, staging enterprise qualification, DAST, load, payment-chaos, backup/restore, and HA evidence gates before production certification.
