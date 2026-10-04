# Organizer-owned team, ticket QR fix and ticket email (on top of the admin refactor)

## Critical fixes found during review
1. **Ticket QR was never sent to the browser.** `/api/v1/public/tickets/{id}` returned no `qrDataUri`, but the ticket page renders it, so every ticket (paid and complimentary) showed a broken QR image. The backend now returns the QR PNG (`TicketQueryService.TicketView.qrDataUri`). The credential itself was already generated, HMAC-signed and its hash stored at issue time, so scanning logic is unchanged. Guarded by `TicketQrAndMailContractTest`.
2. **The platform never sent any email.** No mail code existed, so complimentary ticket emails could not arrive. New `TicketMailService` emails the QR (inline) plus a ticket link:
   - paid orders: queued **after the payment transaction commits**, 3 attempts, never able to affect or roll back a sale;
   - complimentary tickets: sent right after issuing, and the screen now shows the real result (sent / not configured / failed) with an **Email again** button (`POST /api/v1/admin/manager-tickets/orders/{orderId}/email`, scoped to the actor's events, rate limited).
   Safety net unchanged: the ticket page, `/recover`, reconciliation jobs and auto-refund for failed fulfilment.

## Organizer-owned team
- Migration `V19__organizer_scoped_team.sql`: reuses `organizer_members` (roles `STAFF` / `EVENT_MANAGER`) with a partial unique index so a member belongs to one organizer; adds `users.must_change_password` and `user_invites` (hash-only, single-use, 48h). Backfills from event assignments where exactly one organizer is implied; ambiguous accounts stay unlinked and an ADMIN adopts them on first assignment.
- New endpoints: `GET/POST /admin/organizers/{slug}/team[/staff|/managers|/owners]`, `PATCH .../team/{userId}`, `POST .../team/{userId}/invite`, `GET /admin/events/{id}/team`, `PUT|DELETE /admin/events/{id}/staff`.
- Authorization via `EventAccessService.requireOrganizerAccess`: ADMIN any organizer, ORGANIZER only owned organizers, everyone else 403. Assigning someone from another organizer returns 409 `DIFFERENT_ORGANIZER`.
- Invites (when SMTP is configured) or initial password with forced change at first sign-in (`/change-password`, enforced server side in `JwtAuthFilter`). Passwords are never returned or logged.
- Deactivation disables the user immediately (`JwtAuthFilter` rejects disabled users on every request, so scanning stops), revokes refresh tokens and closes invites.
- Legacy `POST /admin/staff` and `/admin/managers` remain, ADMIN only, marked deprecated with a `Deprecation` header.
- Frontend: `/admin/team` rebuilt (organizer switcher, Gate staff / Event managers tabs, search, side drawer, pickers, per-event gate table with Change gate / Remove, empty states, confirmations, toasts, skeletons); same panel inside the event editor; `/accept-invite` and `/change-password` pages.

## Tests added
`TeamServiceTest`, `TeamAuthContractTest`, `TicketQrAndMailContractTest`, plus new cases in `EventAccessServiceTest`.

## Not verified in the build environment
`mvn clean verify`, `ng build` and browser screenshots could not be run where this was authored (no Maven, no npm registry access). Java and TypeScript were syntax-checked only. Run them before release (see ADMIN-SETUP-GUIDE.md, "Email setup").
