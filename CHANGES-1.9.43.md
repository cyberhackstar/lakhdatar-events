# Neelastack Events v1.9.43

## Production payment + recovery correction

- Production browser API calls are locked to the same-origin `/api/v1` path; SSR continues to use the private `http://backend:8080/api/v1` network only.
- Cashfree hosted-return verification now treats non-captured returns as a normal `PENDING`/`CANCELLED` result instead of converting the customer into a forced recovery redirect.
- Payment result pages keep the Neelastack order reference visible while retrying server-side verification briefly.
- Recovery now accepts either the Neelastack `LK-...` order number or the provider/Cashfree transaction ID together with the checkout email.
- Recovery has an explicit timeout/finalizer so the submit button cannot remain stuck forever, and pending/refund/cancelled states are explained.
- Backend payment/recovery invariants remain server-authoritative; no payment is treated as successful from the browser redirect alone.
- CI now rejects production browser bundles that leak the public backend `:8080` URL, preventing this routing/CSP regression from shipping again.
