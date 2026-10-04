# Production Deploy — v1.9.44

1. Build and publish the v1.9.44 backend/web images.
2. Deploy using the existing production compose/deploy procedure.
3. Confirm the running backend reports `1.9.44`.
4. Hard-refresh the admin browser after deployment.
5. Open an event and click **Export CSV** from both the event list and event operations page.
6. Confirm the browser downloads `<event-slug>-attendees.csv` without opening a new protected navigation request.
7. Confirm unauthenticated users still cannot access the CSV endpoint.
