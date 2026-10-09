const { test, expect } = require('@playwright/test');
const { required } = require('./helpers');

test.describe('customer ticket access', () => {
  test('ticket endpoint requires a valid access token and PDF is downloadable', async ({ request }) => {
    const ticketId = required('E2E_TICKET_ID');
    const token = required('E2E_TICKET_TOKEN');
    const valid = await request.get(`/api/v1/public/tickets/${ticketId}`, {
      headers: { 'X-Ticket-Token': token },
    });
    expect(valid.status()).toBe(200);
    const ticket = await valid.json();
    expect(ticket.ticketNumber).toBeTruthy();
    expect(ticket.ticketPosition).toBeGreaterThanOrEqual(1);
    expect(ticket.orderTicketCount).toBeGreaterThanOrEqual(ticket.ticketPosition);

    const invalid = await request.get(`/api/v1/public/tickets/${ticketId}`, {
      headers: { 'X-Ticket-Token': `${token}tampered` },
    });
    expect([401, 403]).toContain(invalid.status());

    const pdf = await request.get(`/api/v1/public/tickets/${ticketId}/pdf`, {
      headers: { 'X-Ticket-Token': token },
    });
    expect(pdf.status()).toBe(200);
    expect(pdf.headers()['content-type']).toContain('application/pdf');
    expect((await pdf.body()).length).toBeGreaterThan(1000);
  });

  test('ticket browser route does not expose access token in the HTTP query string', async ({ page }) => {
    const ticketId = required('E2E_TICKET_ID');
    const token = required('E2E_TICKET_TOKEN');
    const response = await page.goto(`/ticket/${ticketId}#access=${encodeURIComponent(token)}`, { waitUntil: 'domcontentloaded' });
    expect(response.status()).toBe(200);
    await page.waitForLoadState('networkidle');
    expect(page.url()).not.toContain('?access=');
    expect(page.url()).not.toContain('#access=');
  });
});
