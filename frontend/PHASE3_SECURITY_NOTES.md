# Phase 3 Security Notes

- Access tokens remain memory-only.
- Refresh credentials are HttpOnly SameSite=Strict cookies.
- Explicit browser logout sets a local marker so an interrupted logout request cannot immediately re-authenticate the SPA from the still-live HttpOnly cookie.
- Recovery pages persist only a non-secret order number hint with a short retention period; customer email remains required to retrieve ticket access.
- Ticket access tokens use a dedicated 24-hour default TTL and are passed in an HTTP header, not a query string.
