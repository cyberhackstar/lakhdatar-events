const { test, expect } = require('@playwright/test');

test.describe('public surface and security headers', () => {
  test('home and catalog are healthy and protected', async ({ request, page }) => {
    const home = await request.get('/');
    expect(home.status()).toBe(200);
    const headers = home.headers();
    expect(headers['content-security-policy']).toBeTruthy();
    expect(headers['x-content-type-options']).toBe('nosniff');
    expect(headers['x-frame-options']).toBeTruthy();
    expect(headers['strict-transport-security']).toBeTruthy();

    const catalog = await request.get('/events');
    expect(catalog.status()).toBe(200);
    await page.goto('/events', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('body')).not.toContainText('Internal Server Error');
  });

  test('privileged API is not publicly accessible', async ({ request }) => {
    const response = await request.get('/api/v1/admin/dashboard');
    expect([401, 403]).toContain(response.status());
  });
});
