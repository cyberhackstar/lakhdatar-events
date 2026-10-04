# Production Deployment — v1.9.48

1. Build and publish the backend/frontend images from this release.
2. Deploy using the existing production compose/deploy workflow.
3. Confirm `/actuator/health` is healthy and Flyway completes successfully.
4. Purge Cloudflare cache after frontend deployment.
5. Hard-refresh one admin browser session before testing event creation.
6. Validate: event create, authenticated attendee CSV, Cashfree success/cancel/pending return, recovery by Neelastack order number and provider transaction ID.

## Release gate
Do not promote unless GitHub Actions Maven and frontend CI are green.
