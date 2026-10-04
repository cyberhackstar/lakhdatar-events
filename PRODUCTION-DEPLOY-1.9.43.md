# Neelastack Events v1.9.43 — Production deployment

1. Build and publish the backend, web, and edge images through the existing GitHub Actions `main` pipeline.
2. Deploy the same image tag to the Oracle VM.
3. Purge the Cloudflare cache for `events.neelastack.com` and hard-refresh the browser once after deployment so no previously cached HTML/chunk is reused.
4. Verify browser DevTools Network requests are same-origin paths such as `/api/v1/admin/events` and never `http://events.neelastack.com:8080/...`.
5. Test a Cashfree cancellation: the user must remain on the payment-result screen and must not be forced to `/recover`.
6. Test a successful Cashfree payment: the payment-result screen should confirm the order and show issued tickets; brief provider/webhook propagation is handled by retry.
7. Test recovery with both the `LK-...` order number and the Cashfree transaction ID plus the checkout email.

Security action: any SMTP credential/application password that was pasted into chat/server output must be rotated immediately and removed from deployment history/secrets.
