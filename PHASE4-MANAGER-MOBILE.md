# Phase 4 — Event-manager scope, complimentary tickets and mobile stability

## Business model

- **Neelastack** owns the platform.
- **Lakhdatar Events** is the current organizer using the platform.
- `EVENT_MANAGER` access is scoped to explicit event assignments; organizer membership alone never grants event-manager access.

## Security and operations

- Manager dashboards and staff/scanner event lists return only explicitly assigned events.
- Publish, ticket-type, staff assignment, attendee export and complimentary-issuance paths enforce event scope at the service boundary; refunds remain restricted to ADMIN/FINANCE/ORGANIZER financial roles.
- Scanner authorization is performed before event-state validation to reduce event-state probing through differential responses.
- Manager-issued complimentary tickets consume real inventory, cost ₹0, are audited, and use `COMPLIMENTARY_MANAGER` provenance with `issued_by_user_id`.
- Customer tickets and scanner results display the manager-issued provenance and issuer name.

## Mobile

- Editable `input`, `select` and `textarea` controls are kept at 16px or larger to avoid Safari/iOS focus zoom.
- The viewport remains accessible; the application does not disable user zoom with `user-scalable=no` or `maximum-scale=1`.
- Admin ticket forms no longer require a fixed 650px mobile row.

## Refund lifecycle

- Event cancellation immediately invalidates issued tickets and closes sales.
- A scheduled cancellation-refund job queues captured/completed payments without an existing refund, while the existing refund recovery job performs/retries Razorpay operations asynchronously.
- Event cancellation never blocks the admin request on a provider API call.

## Deferred phase 2

- Full rich event-editing UI and mobile admin card/table redesign can be expanded without changing the security model above.
