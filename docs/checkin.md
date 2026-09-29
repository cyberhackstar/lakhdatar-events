# Event-day QR check-in

## Device requirements

- Android or iPhone
- modern mobile browser
- HTTPS
- camera permission
- network connectivity

No dedicated scanner hardware is required.

## Flow

```text
Staff login → event/gate selection → camera → QR decode → API → atomic validation → result → next scan
```

## Server authority

The scanner UI never decides admission. The backend does.

If the phone is offline or the API is unavailable, the UI shows a verification failure and does not grant entry.

## Double-scan defense

The backend obtains a pessimistic row lock on the ticket. Only the transaction that changes `ISSUED` to `CHECKED_IN` can return `ACCEPTED`. A concurrent request observes `CHECKED_IN` and returns `ALREADY_USED`.

The suite contains a required 100-concurrent-scan integration test against PostgreSQL/Testcontainers.

## Gate ACL

A plain staff account is assigned to an event and optional gate. Organizer/event-manager/admin roles can operate across their authorized events. Backend authorization is enforced even if a malicious client changes the URL/event ID.
