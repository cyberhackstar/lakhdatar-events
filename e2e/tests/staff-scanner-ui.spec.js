const { test, expect } = require('@playwright/test');
const { required } = require('./helpers');

test.describe('staff scanner browser qualification', () => {
  test('staff can reach the event-day scanner on the actual browser profile', async ({ page }) => {
    const email = required('E2E_STAFF_EMAIL');
    const password = required('E2E_STAFF_PASSWORD');
    const eventId = required('E2E_EVENT_ID');

    await page.goto('/login', { waitUntil: 'domcontentloaded' });
    await page.getByLabel('Email').fill(email);
    await page.getByLabel('Password').fill(password);
    await page.getByRole('button', { name: /sign in/i }).click();
    await page.waitForURL(/\/staff(\/|$)/, { timeout: 20_000 });

    await page.goto(`/staff/events/${encodeURIComponent(eventId)}/scanner?gate=Main%20Gate&eventName=Enterprise%20E2E`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.scanner')).toBeVisible();
    await expect(page.locator('.online, .offline-banner')).toBeVisible();
    await expect(page.locator('footer')).toContainText('Server-authoritative validation');
    await expect(page.locator('#qr-reader:visible, .state-card:visible').first()).toBeVisible();

    const cameraFallback = page.getByRole('button', { name: /manual entry/i });
    if (await cameraFallback.isVisible().catch(() => false)) {
      await cameraFallback.click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await expect(page.getByRole('dialog', { name: 'Enter ticket code' }).getByRole('textbox', { name: 'Ticket code' })).toHaveAttribute('maxlength', '512');
    }
  });
});
