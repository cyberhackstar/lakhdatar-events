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

    // The Spring endpoint is canonicalized WITHOUT a trailing slash. maxRedirects=0
    // deliberately catches an NGINX auto-redirect instead of masking it.
    const catalogApi = await request.get('/api/v1/public/events?page=0&size=12', { maxRedirects: 0 });
    expect(catalogApi.status()).toBe(200);
    const catalogBody = await catalogApi.json();
    expect(catalogBody.page).toBe(0);
    expect(catalogBody.size).toBe(12);
    expect(Array.isArray(catalogBody.items)).toBeTruthy();

    const catalog = await request.get('/events');
    expect(catalog.status()).toBe(200);
    await page.goto('/events', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('body')).not.toContainText('Internal Server Error');
  });

  test('privileged API is not publicly accessible', async ({ request }) => {
    const response = await request.get('/api/v1/admin/dashboard');
    expect([401, 403]).toContain(response.status());
  });

  test('untrusted event-filter query values are not parsed as active HTML', async ({ page }) => {
    let scriptDialogObserved = false;
    page.on('dialog', async (dialog) => { scriptDialogObserved = true; await dialog.dismiss(); });
    const payload = encodeURIComponent('\"><img src=x onerror=alert(1)>');
    await page.goto(`/events?category=${payload}`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('img[onerror], svg[onload], script[data-xss-probe]')).toHaveCount(0);
    expect(scriptDialogObserved).toBe(false);
  });
});
