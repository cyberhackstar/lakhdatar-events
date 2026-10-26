'use strict';

const { test } = require('@playwright/test');
const { api: mailApi } = require('../payment-qualification/mailbox.cjs');
const { CHECKS, blankReport, writeReport } = require('../payment-qualification/report.cjs');

test.describe.configure({ mode: 'serial', retries: 0 });
test('complete a real Cashfree sandbox purchase and prove all eight customer-flow controls', async ({ page, request }, testInfo) => {
  test.setTimeout(300000);
  const report = blankReport();
  const checkById = new Map(report.checks.map((entry) => [entry.id, entry]));
  let activeStep = 'payment_confirmed';
  let paidOrder = null;
  let paidOrderNumber = '';
  let transactionId = '';
  let checkoutEmail = process.env.E2E_CHECKOUT_EMAIL || '';
  let orderRow = null;

  function fail(message) {
    const error = new Error(message);
    error.safeDetail = message;
    throw error;
  }
  function record(id, status, detail) {
    const item = checkById.get(id);
    item.status = status;
    item.detail = String(detail || '').slice(0, 240);
  }
  async function step(id, fn) {
    activeStep = id;
    try {
      const detail = await fn();
      record(id, 'PASS', detail || 'Verified by the live staging application.');
      writeReport(report);
      return detail;
    } catch (error) {
      record(id, 'FAIL', error && error.safeDetail ? error.safeDetail : 'Automation failed (' + ((error && error.name) || 'Error') + '). See sanitized Playwright logs; response bodies and credentials are intentionally omitted.');
      writeReport(report);
      throw error;
    }
  }
  function ensure(condition, message) {
    if (!condition) fail(message);
  }
  function auth() {
    const token = String(process.env.E2E_ADMIN_BEARER || '').replace(/^Bearer\s+/i, '').trim();
    ensure(token.length > 20, 'Disposable provisioning did not supply a valid admin session.');
    return { Authorization: 'Bearer ' + token };
  }
  async function adminOrderList() {
    const response = await request.get('/api/v1/admin/events/' + encodeURIComponent(process.env.E2E_EVENT_ID) + '/orders?q=' + encodeURIComponent(paidOrderNumber) + '&page=0&size=50', { headers: auth() });
    ensure(response.ok(), 'The staging admin order API returned HTTP ' + response.status() + '.');
    const data = await response.json();
    return Array.isArray(data.items) ? data.items : [];
  }
  async function findPaidOrder(waitForCapture) {
    const deadline = Date.now() + (waitForCapture ? 120000 : 15000);
    let last = null;
    do {
      const rows = await adminOrderList();
      const matches = rows.filter((item) => item.orderNumber === paidOrderNumber);
      if (matches.length > 1) fail('More than one order has the paid order number.');
      last = matches[0] || null;
      if (last && (!waitForCapture || (last.status === 'CONFIRMED' && ['CAPTURED', 'COMPLETED'].includes(String(last.paymentStatus).toUpperCase())))) return last;
      if (!waitForCapture) break;
      await page.waitForTimeout(4000);
    } while (Date.now() < deadline);
    if (!last) fail('The paid order was not present in the staging admin order API.');
    if (waitForCapture) fail('The provider payment did not reach CONFIRMED with CAPTURED/COMPLETED payment status within two minutes.');
    return last;
  }
  async function clickVisibleInFrames(regex) {
    for (const frame of [...page.frames()].reverse()) {
      const candidates = [
        frame.getByRole('tab', { name: regex }),
        frame.getByRole('button', { name: regex }),
        frame.getByRole('radio', { name: regex }),
        frame.getByText(regex, { exact: true }),
      ];
      for (const locator of candidates) {
        try {
          const count = await locator.count();
          for (let i = 0; i < Math.min(count, 5); i++) {
            const item = locator.nth(i);
            if (await item.isVisible()) { await item.click({ timeout: 5000 }); return true; }
          }
        } catch { /* try the next public UI candidate; never inspect provider secrets */ }
      }
    }
    return false;
  }
  async function fillUpiInFrames(value) {
    for (const frame of [...page.frames()].reverse()) {
      const locator = frame.locator('input[placeholder*="UPI" i],input[aria-label*="UPI" i],input[name*="upi" i],input[placeholder*="VPA" i],input[aria-label*="VPA" i]');
      try {
        const count = await locator.count();
        for (let i = 0; i < Math.min(count, 5); i++) {
          const item = locator.nth(i);
          if (await item.isVisible()) { await item.fill(value, { timeout: 5000 }); return true; }
        }
      } catch { /* try the next hosted-checkout frame */ }
    }
    return false;
  }
  async function clickPaymentActionInFrames() {
    return clickVisibleInFrames(/verify|pay now|pay ₹|continue|proceed|submit/i);
  }
  async function getLatestEmail() {
    const inboxId = String(process.env.MAILSLURP_INBOX_ID || '').trim();
    const since = String(process.env.E2E_PAYMENT_MAIL_SINCE || '').trim();
    ensure(inboxId && since, 'The disposable inbox or post-provision email timestamp is missing.');
    const qs = new URLSearchParams({ inboxId, timeout: '120000', since, unreadOnly: 'true' });
    const email = await mailApi('/waitForLatestEmail?' + qs.toString());
    let body = [email.body, email.bodyExcerpt, email.text, email.html, email.subject].filter(Boolean).join('\n');
    if (email.id && body.length < 100) {
      const full = await mailApi('/emails/' + encodeURIComponent(email.id));
      body = [full.body, full.bodyExcerpt, full.text, full.html, full.subject].filter(Boolean).join('\n');
    }
    return body;
  }

  try {
    ensure(testInfo.project.name === 'chromium', 'The stateful payment qualification must run in Chromium only.');
    ensure((process.env.E2E_ENV || '').toLowerCase() === 'staging', 'This payment test is staging-only.');
    ensure(process.env.E2E_RUN_MUTATIONS === 'true' && process.env.E2E_RUN_PAYMENT_FLOW === 'true', 'Paid mutations were not explicitly enabled.');
    ensure((process.env.DEFAULT_PAYMENT_PROVIDER || '').toUpperCase() === 'CASHFREE', 'This run requires Cashfree as the selected provider.');
    ensure(checkoutEmail && process.env.MAILSLURP_INBOX_ID, 'A disposable MailSlurp inbox is required before fixture provisioning.');
    ensure(process.env.E2E_EVENT_SLUG && process.env.E2E_EVENT_ID && process.env.E2E_TICKET_TYPE_ID, 'The disposable staging event fixture is incomplete.');

    await step('payment_confirmed', async () => {
      await page.goto('/events/' + encodeURIComponent(process.env.E2E_EVENT_SLUG), { waitUntil: 'domcontentloaded' });
      await page.locator('.type .qty button[aria-label="Increase"]').first().click();
      await page.locator('.book button.btn-primary').first().click();
      await page.locator('#customerName').waitFor({ state: 'visible', timeout: 20000 });
      await page.locator('#customerName').fill('Neelastack Payment Qualification');
      await page.locator('#customerEmail').fill(checkoutEmail);
      await page.locator('#customerPhone').fill('9876543210');
      await page.locator('input[formControlName="accepted"]').check();

      const checkoutResponsePromise = page.waitForResponse((response) =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname.endsWith('/api/v1/public/checkout'), { timeout: 45000 });
      await page.locator('button.pay').click();
      const checkoutResponse = await checkoutResponsePromise;
      ensure(checkoutResponse.status() === 200, 'Sandbox checkout creation returned HTTP ' + checkoutResponse.status() + '.');
      paidOrder = await checkoutResponse.json();
      ensure(paidOrder && paidOrder.orderNumber && paidOrder.providerOrderId, 'Checkout response omitted required order references.');
      ensure(String(paidOrder.provider).toUpperCase() === 'CASHFREE', 'The created order did not use Cashfree.');
      ensure(String(paidOrder.providerCheckoutMode).toLowerCase() === 'sandbox', 'The created checkout session is not explicitly marked sandbox; refusing to proceed.');
      paidOrderNumber = String(paidOrder.orderNumber);

      await page.waitForURL((url) => /(^|\.)cashfree\.com$/i.test(url.hostname) || /\.cashfree\.com$/i.test(url.hostname), { timeout: 60000 });
      const upiTabClicked = await clickVisibleInFrames(/^\s*UPI\s*$/i) || await clickVisibleInFrames(/UPI ID/i);
      ensure(upiTabClicked, 'Cashfree sandbox did not expose a selectable UPI payment method.');
      const upiFilled = await fillUpiInFrames('testsuccess@gocash');
      ensure(upiFilled, 'Cashfree sandbox UPI ID field was not found in the hosted checkout.');
      const actionClicked = await clickPaymentActionInFrames();
      ensure(actionClicked, 'Cashfree sandbox did not expose a payment/verification action for UPI.');
      await page.waitForURL((url) => url.origin === 'https://staging-events.neelastack.com' && url.pathname === '/payment/success' && url.searchParams.has('order_id'), { timeout: 180000 });
      await page.locator('.order-chip').first().waitFor({ state: 'visible', timeout: 120000 });
      const eyebrow = await page.locator('.eyebrow').first().innerText().catch(() => '');
      ensure(/payment confirmed/i.test(eyebrow), 'The customer return page did not show confirmed payment state.');
      orderRow = await findPaidOrder(true);
      return 'Cashfree session was sandbox; customer return and server order/payment state are confirmed.';
    });

    await step('single_ticket_set', async () => {
      const rows = await adminOrderList();
      const exact = rows.filter((item) => item.orderNumber === paidOrderNumber);
      ensure(exact.length === 1, 'The admin order query did not return exactly one order for this payment.');
      const current = exact[0];
      ensure(current.status === 'CONFIRMED', 'The paid order is not in CONFIRMED state.');
      ensure(['CAPTURED', 'COMPLETED'].includes(String(current.paymentStatus).toUpperCase()), 'The payment record is not CAPTURED/COMPLETED.');
      ensure(Number(current.ticketCount) === 1, 'Expected exactly one issued ticket for the one-ticket sandbox purchase.');
      orderRow = current;
      return 'One confirmed order and exactly one issued ticket; no duplicate order or ticket was observed.';
    });

    await step('customer_references', async () => {
      const orderBox = page.locator('.order-chip').filter({ hasText: 'NEELASTACK ORDER NUMBER' });
      const shownOrder = (await orderBox.locator('strong').innerText()).trim();
      ensure(shownOrder === paidOrderNumber, 'The payment result order number did not match the created order.');
      const txBox = page.locator('.order-chip').filter({ hasText: 'PAYMENT TRANSACTION ID' });
      transactionId = (await txBox.locator('strong').innerText()).trim();
      ensure(transactionId.length > 0, 'The payment result did not display a provider transaction ID.');
      return 'The exact order number and a non-empty provider transaction ID are visible on the customer payment result.';
    });

    await step('ticket_email', async () => {
      const body = await getLatestEmail();
      ensure(body.includes(paidOrderNumber), 'The new ticket email did not contain the paid order number.');
      ensure(body.includes(transactionId), 'The new ticket email did not contain the provider transaction ID shown on the payment result.');
      ensure(/ticket|qr|entry/i.test(body), 'The email did not contain expected ticket content.');
      return 'A new inbox email received after checkout contains the same order number, provider transaction ID, and ticket content.';
    });

    await step('same_email_recovery', async () => {
      await page.goto('/recover', { waitUntil: 'domcontentloaded' });
      await page.locator('#orderNumber').fill(paidOrderNumber);
      await page.locator('#email').fill(checkoutEmail);
      await page.getByRole('button', { name: /Recover tickets/i }).click();
      const found = page.locator('.found');
      await found.waitFor({ state: 'visible', timeout: 30000 });
      const text = await found.innerText();
      ensure(text.includes(paidOrderNumber), 'Recovery did not show the paid order number.');
      ensure(text.includes(transactionId), 'Recovery did not show the provider transaction ID.');
      ensure(/1 ticket found/i.test(text), 'Recovery did not return exactly the paid order ticket.');
      return 'Recovery using the original checkout email returned the paid order and its one ticket.';
    });

    await step('replay_privacy', async () => {
      // The Playwright request fixture has no browser checkout cookies. Send two identical
      // verification requests without checkout credentials to exercise the confirmed replay path.
      for (let attempt = 0; attempt < 2; attempt++) {
        const response = await request.post('/api/v1/public/checkout/verify', { data: { providerOrderId: paidOrder.providerOrderId } });
        ensure(response.status() === 200, 'Unauthenticated idempotent verification replay returned HTTP ' + response.status() + '.');
        const data = await response.json();
        ensure(data.status === 'CONFIRMED', 'Verification replay did not return confirmed status only.');
        ensure(Array.isArray(data.tickets) && data.tickets.length === 0, 'An unauthorized replay returned ticket credentials.');
        ensure(!Object.prototype.hasOwnProperty.call(data, 'transactionId') || data.transactionId == null, 'An unauthorized replay exposed a provider transaction reference.');
        ensure(!JSON.stringify(data).includes('accessToken'), 'An unauthorized replay response contained a ticket access-token field.');
      }
      const rows = await adminOrderList();
      const exact = rows.filter((item) => item.orderNumber === paidOrderNumber);
      ensure(exact.length === 1 && Number(exact[0].ticketCount) === 1, 'Repeated verification changed order count or issued duplicate tickets.');
      return 'Two no-cookie replay requests returned status only with no ticket references/tokens; database-backed admin view still shows one order and one ticket.';
    });

    await step('ledger_reconciliation', async () => {
      const response = await request.get('/api/v1/finance/ledger?q=' + encodeURIComponent(paidOrderNumber) + '&entryType=SALE&page=0&size=20', { headers: auth() });
      ensure(response.ok(), 'Finance ledger API returned HTTP ' + response.status() + '.');
      const data = await response.json();
      const sales = (Array.isArray(data.items) ? data.items : []).filter((item) => item.orderNumber === paidOrderNumber && item.entryType === 'SALE');
      ensure(sales.length === 1, 'The paid order did not have exactly one SALE ledger entry.');
      const sale = sales[0];
      ensure(Number(sale.amountMinor) === Number(orderRow.totalMinorUnits), 'Ledger SALE amount does not match the order total.');
      ensure(String(sale.currency).toUpperCase() === String(orderRow.currency).toUpperCase(), 'Ledger SALE currency does not match the order currency.');
      ensure(Number(orderRow.totalMinorUnits) === Number(paidOrder.amountMinorUnits), 'Admin order total does not match the checkout amount.');
      return 'Exactly one SALE ledger entry matches order amount/currency and the provider-confirmed payment state.';
    });
  } catch (error) {
    for (const item of report.checks) {
      if (item.status === 'BLOCKED') {
        item.detail = item.id === 'cleanup' ? 'Cleanup result pending the protected fixture teardown and inbox deletion step.' : 'Not completed because check ' + activeStep + ' failed or was blocked.';
      }
    }
    writeReport(report);
    throw error;
  } finally {
    // Report sanitized evidence only. The real fixture cleanup is awaited by Playwright global setup's
    // returned teardown, and that teardown updates check 8 after it actually finishes.
    writeReport(report);
  }
});
