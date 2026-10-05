const { test, expect } = require('@playwright/test');

test.describe.configure({ mode: 'serial', retries: 0 });
const { required, bearer } = require('./helpers');

test.describe('critical business APIs', () => {
  test.beforeEach(async ({}, testInfo) => {
    test.skip(testInfo.project.name !== 'chromium', 'stateful staging mutation suite runs once per release');
  });
  test('admin and staff authorization surfaces work', async ({ request }) => {
    const admin = await request.get('/api/v1/admin/dashboard', { headers: { Authorization: bearer('E2E_ADMIN_BEARER') } });
    expect(admin.status()).toBe(200);
    const staff = await request.get('/api/v1/staff/events', { headers: { Authorization: bearer('E2E_STAFF_BEARER') } });
    expect(staff.status()).toBe(200);
  });

  test('checkout idempotency returns the same logical order on retry', async ({ request }) => {
    const eventId = required('E2E_EVENT_ID');
    const ticketTypeId = required('E2E_TICKET_TYPE_ID');
    const email = required('E2E_CHECKOUT_EMAIL');
    const key = required('E2E_IDEMPOTENCY_KEY');
    const body = {
      eventId,
      customerName: 'Enterprise E2E',
      customerEmail: email,
      customerPhone: '+919999999999',
      idempotencyKey: key,
      items: [{ ticketTypeId, quantity: 1 }],
    };
    const first = await request.post('/api/v1/public/checkout', { data: body });
    expect(first.status()).toBe(200);
    const a = await first.json();
    const second = await request.post('/api/v1/public/checkout', { data: body });
    expect(second.status()).toBe(200);
    const b = await second.json();
    expect(b.orderPublicId).toBe(a.orderPublicId);
  });

  test('a real QR credential can only be accepted once', async ({ request }) => {
    const response = await request.post('/api/v1/checkin/scan', {
      headers: { Authorization: bearer('E2E_STAFF_BEARER'), 'Content-Type': 'application/json' },
      data: {
        eventId: required('E2E_EVENT_ID'),
        qrToken: required('E2E_QR_TOKEN'),
        gate: required('E2E_GATE'),
      },
    });
    expect(response.status()).toBe(200);
    const first = await response.json();
    expect(first.result).toBeTruthy();

    const secondResponse = await request.post('/api/v1/checkin/scan', {
      headers: { Authorization: bearer('E2E_STAFF_BEARER'), 'Content-Type': 'application/json' },
      data: {
        eventId: required('E2E_EVENT_ID'),
        qrToken: required('E2E_QR_TOKEN'),
        gate: required('E2E_GATE'),
      },
    });
    expect(secondResponse.status()).toBe(200);
    const second = await secondResponse.json();
    expect(second.result).toBe('ALREADY_USED');
  });
});
