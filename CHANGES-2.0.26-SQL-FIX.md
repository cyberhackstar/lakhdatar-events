# v2.0.26 SQL Fix

## Ticket reservation release

Fixed `TicketReservationRepository.releaseHeldForEventAndAdjustInventory`.

The `ticket_reservations` table does not contain an `event_id` column. Event ownership is derived through `ticket_reservations.ticket_type_id -> ticket_types.event_id`.

The native release query now performs the update through `ticket_types` and scopes held reservations with `tt.event_id = :eventId`, preventing `column "event_id" does not exist` failures and preserving event isolation.
