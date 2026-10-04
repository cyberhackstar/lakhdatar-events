# Changes — v1.9.44

## Production CSV export authentication fix

- Fixed admin attendee CSV downloads on `/admin/events` and event operations.
- Removed native unauthenticated browser navigation for protected CSV endpoints.
- CSV export now uses the existing authenticated Angular `ApiService` request, so the auth interceptor attaches the current bearer token and can refresh an expired access token before retrying.
- The authenticated blob is converted to a local object URL and downloaded in-browser.
- Added explicit error handling so export buttons never remain stuck when the API returns an error.

## Why v1.9.43 failed in production

The previous implementation used a plain `<a href="/api/v1/admin/events/{id}/attendees.csv">` navigation. The application keeps the access token in memory and sends it through Angular's HTTP interceptor, so a browser navigation does not include the required `Authorization` header. The protected endpoint therefore returned 401 and Chrome reported the file as unavailable.

## Scope

No backend authentication weakening was introduced. The CSV endpoint remains protected by the normal admin authorization chain.
