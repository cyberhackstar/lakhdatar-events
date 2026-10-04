# Validation — v1.9.44

- Source reviewed for authenticated CSV export path.
- `ApiService.attendeesCsv()` remains the protected HTTP endpoint used by both admin CSV surfaces.
- Both admin CSV actions now use `ApiService.attendeesCsv()` rather than native browser navigation.
- Error paths clear the busy state.
- Downloaded blobs are revoked after download to avoid object-URL leaks.
- Backend CSV response retains `Content-Disposition: attachment`, `Cache-Control: no-store, private`, and `X-Content-Type-Options: nosniff`.
- Production CI (GitHub Actions) remains the authoritative full Maven/Angular verification.
