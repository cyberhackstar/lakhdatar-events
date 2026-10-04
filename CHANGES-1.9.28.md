# v1.9.28 — Final organizer operations + checkout + ticket sharing

## Customer checkout and ticket delivery
- Preserves the v1.9.27 provider-recovery fix: explicit provider NOT_FOUND clears a stale local provider order so checkout can safely recreate it; transient provider failures remain fail-closed.
- Fixed the generated ticket page to read `ActivatedRoute.snapshot.fragment` when receiving `#access=...`; Share and Save as PDF links therefore authenticate correctly after being opened in a new tab or another device.
- Share uses the native browser share sheet when available and falls back to copying the secure ticket URL.
- Save as PDF opens a clean ticket-only browser PDF flow; navigation/action controls are hidden while organizer and Neelastack branding remain printable.
- Payment-result and recovery pages expose Share / Save PDF for every issued ticket.

## Organizer / platform operations
- Keeps the top-level Issued Tickets console with platform-admin, organizer-owner and event-manager scope enforced on the server.
- Event Operations retains event-scoped Issued Tickets, Orders, attendees CSV and report metrics.
- Existing organizer hierarchy is preserved: Neelastack Platform Admin → Organizer → Organizer Owner / team → Event Manager / Gate Staff → assigned event.
- Organizer owners can manage their own operational team; platform admins retain cross-organizer oversight.

## Safety / robustness
- Administrative search input is bounded to 100 characters before SQL wildcard construction.
- Existing tests are preserved; only additive contract coverage is used.
- No database migration is required for this release; existing Flyway V1–V24 schema remains unchanged.
