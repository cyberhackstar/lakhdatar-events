# Validation — v2.0.28

## Fix verified at source level

- Root cause from the v2.0.27 runtime test is identified: PDFBox throws `IllegalArgumentException: Parameters must be within 0..1, but are (24.00,19.00,27.00)` when the PDF renderer passes 8-bit RGB values to the normalized float API.
- `TicketPdfService` now converts every 8-bit RGB component to the required 0.0–1.0 range and validates the source component range first.
- No raw 0–255 arguments remain in the PDF renderer's color API calls.
- The real PDF regression test remains in place and validates a parseable PDF with an embedded QR image.

## Runtime verification required

The supplied Windows Maven run against v2.0.27 executed 237 tests and reported exactly one error, `TicketPdfServiceTest.generatesValidPdfWithServerGeneratedQrImageInHeadlessRuntime`; all other completed tests in that run reported zero failures/errors. The failure was the PDFBox color-component exception documented above.

This environment does not have Maven installed, so the v2.0.28 runtime test cannot be truthfully marked PASS here. Run the following on the development/CI runner:

1. `mvn -B -ntp clean verify`
2. Build/restart the local stack without deleting the PostgreSQL volume.
3. Run the full Chromium non-mutating E2E suite and verify the customer ticket PDF returns HTTP 200 and is a valid PDF.
4. Run the enterprise staging qualification against the exact release SHA.
5. Promote to production only after protected certification evidence is PASS.
