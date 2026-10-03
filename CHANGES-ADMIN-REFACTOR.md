# Admin console refactor + logo fix (on top of 1.9.22)

## Logo
- Root cause of the broken logo: `/assets/neelastack-logo.png` was answered with HTTP 302 -> /not-found, i.e. the file was not inside the deployed web image. A missing asset used to fall into the Angular catch-all route.
- `frontend/src/server.ts`: missing `/assets/*` now returns a real 404.
- `frontend/Dockerfile` and `.github/workflows/ci.yml`: build fails if `neelastack-logo.png` is missing from the output.
- New `shared/brand-mark.component.ts`: logo image + "Events" text (no duplicate "Neelastack" text). Falls back to a text wordmark if the image fails. Used in the public header, login, staff page and admin sidebar.
- **Put your real logo at `frontend/src/assets/neelastack-logo.png`** (any proportions, transparent PNG, designed for a dark background). The bundled file is only a placeholder.

## Admin console (`/admin/*`, real routes, no #anchors)
- `/admin` overview, `/admin/events` list (search/filter/publish/CSV), `/admin/events/new`, `/admin/events/:id` editor, `/admin/organizers`, `/admin/team`, `/admin/complimentary`.
- Fixed: sidebar `#anchor` links jumped to the home page (`<base href="/">`); no navigation on mobile; invisible ticket inputs and dark autofill/select bars (global `color-scheme: dark`); save success messages cleared instantly; wrong "...ed" messages; editor allowed price 0 (backend rejects); create used browser timezone instead of event timezone; create had no payment-provider choice (Cashfree-only servers failed); silent disabled submit button; hidden capacity limit.
- Role guards per route; shared `AdminStore`; `core/datetime.ts` shared helpers.
