# First-time setup: creating the admin and the first organizer

Production lives in `/home/ubuntu/apps/lakhdatar-events`. All secrets live in that folder's `.env` (never in Git).

## 1. Fill the VM `.env` (once)

Generate every secret fresh. Do not reuse values between variables.

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 48   # TICKET_VIEW_SECRET
openssl rand -base64 48   # QR_SIGNING_SECRET
openssl rand -base64 48   # REDIS_PASSWORD   (must be >= 32 chars)
openssl rand -base64 24   # DB_PASSWORD      (must be >= 12 chars)
openssl rand -hex 32      # INITIAL_ADMIN_SETUP_TOKEN (>= 32 bytes)
```

The backend refuses to start in production unless all of these hold (see `ProductionConfigurationGuard`):
`DB_PASSWORD` >= 12 chars; `JWT_SECRET`, `TICKET_VIEW_SECRET`, `QR_SIGNING_SECRET` >= 32 bytes and not `replace-with-*`/`change-me*`;
`REDIS_PASSWORD` >= 32 chars; `AUTH_COOKIE_SECURE=true`; `BOOTSTRAP_ENABLED=false`; every URL `https://`;
at least one payment provider complete (Razorpay: key id + key secret + webhook secret, or Cashfree: app id + secret key).

For logo uploads also set `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`
(the API secret stays on the server; the browser never sees it).

## 2. Turn on the one-time admin setup

In `.env`:

```
INITIAL_ADMIN_SETUP_ENABLED=true
INITIAL_ADMIN_SETUP_TOKEN=<the 64-hex-char value you generated>
```

Redeploy so the backend picks it up (same release tag is fine):

```bash
cd /home/ubuntu/apps/lakhdatar-events
./infra/deploy/deploy.sh "$(cat .deploy-current)"
```

Check it is open (should print `{"enabled":true,"completed":false}`):

```bash
curl -s https://events.neelastack.com/api/v1/setup/initial-admin/status
```

## 3. Create the administrator

Open `https://events.neelastack.com/setup/initial-admin` and fill in:

| Field | Rule |
|---|---|
| Setup token | exactly the `INITIAL_ADMIN_SETUP_TOKEN` value |
| Administrator name | 2-120 characters |
| Email | becomes the login |
| Password | 12-128 characters |
| First organizer name / slug | your event company, e.g. `Lakhdatar Events` / `lakhdatar-events` (lowercase, digits, hyphens) |

This creates the `ADMIN` account and an `OWNER` membership for the first organizer. It never creates a sample event.
The endpoint is rate limited (5 attempts / 10 minutes per IP) and locks itself in the database after the first success.

## 4. Close the setup door

In `.env` set `INITIAL_ADMIN_SETUP_ENABLED=false` (you may clear the token), then redeploy as in step 2.
Even if you forget, a completed setup cannot be repeated, but leaving it off is the correct state.

## 5. Add the organizer's logo (and further organizers)

Sign in at `/login`, then in the console sidebar open **Organizers** (`/admin/organizers`):

- The organizer created in step 3 has a name but no logo yet: click **Add logo** and choose a PNG/JPEG (max 5 MB).
- To add another event company: **Add an organizer** -> name, slug, optional description/website, choose the logo -> **Create organizer**.
  The logo is uploaded to Cloudinary by the backend and only its HTTPS URL is stored. Nothing about organizers comes from env vars.
- If Cloudinary is not configured, the page says so; organizers can still be created and given a logo later.

## 6. First event

Console -> **Create event** -> pick the organizer (a dropdown appears once more than one exists) -> fill details and tickets -> **Create draft event**
-> open **Edit** to set payment provider, branding, cover/banner -> **Publish**.

## 7. Payment webhooks (must match what is configured in the provider dashboards)

- Razorpay: `https://events.neelastack.com/api/v1/webhooks/razorpay` (secret = `RAZORPAY_WEBHOOK_SECRET`)
- Cashfree: `https://events.neelastack.com/api/v1/webhooks/cashfree` (signed with `CASHFREE_SECRET_KEY`)
- Cashfree production base URL is `https://api.cashfree.com/pg` (sandbox is `https://sandbox.cashfree.com/pg`). Never mix test keys with the live URL.

## 8. Replacing the Neelastack logo

Overwrite `frontend/src/assets/neelastack-logo.png` with the new PNG (a square mark works best; it is shown inside fixed square slots and as the favicon),
commit, and let CI build/deploy. If the old logo still shows, purge that URL in Cloudflare (Caching -> Configuration -> Purge by URL).
No env var is involved.
