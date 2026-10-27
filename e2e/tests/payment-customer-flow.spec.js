'use strict';

const { test, expect } = require('@playwright/test');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');

const STAGING_ORIGIN = 'https://staging-events.neelastack.com';
const CHECK_DEFS = [
  { id: 'sandbox_payment', name: 'A Cashfree sandbox payment reaches CONFIRMED' },
  { id: 'exactly_one_ticket', name: 'Exactly one ticket is issued for the paid order' },
  { id: 'order_number_display', name: 'Payment result displays the Neelastack order number' },
  { id: 'transaction_id_display', name: 'Payment result displays the verified provider transaction ID' },
  { id: 'ticket_email_references', name: 'Ticket email contains the order number and provider transaction ID' },
  { id: 'same_email_recovery', name: 'Ticket recovery succeeds with the same checkout email' },
  { id: 'replay_safety', name: 'Repeated verification is idempotent and anonymous replay exposes no ticket credentials' },
  { id: 'ledger_reconciliation', name: 'Exactly one matching SALE ledger entry reconciles to the paid order' },
];

class QualificationError extends Error {}

function guard(condition, message) {
  if (!condition) throw new QualificationError(message);
}

function sandboxCashfreeHost(hostname) {
  const host = String(hostname || '').toLowerCase();
  return host === 'sandbox.cashfree.com'
    || host === 'payments-test.cashfree.com'
    || host.endsWith('.sandbox.cashfree.com')
    || host.endsWith('.test.cashfree.com');
}

function hasCredentialFields(value) {
  if (!value || typeof value !== 'object') return false;
  if (Array.isArray(value)) return value.some(hasCredentialFields);
  return Object.entries(value).some(([key, nested]) => {
    if (/(^|_)(access.?token|ticket.?token|qr.?token|credential|secret|password)(_|$)/i.test(key)) return true;
    return hasCredentialFields(nested);
  });
}

async function findVisibleInFrames(page, selectors) {
  for (const frame of page.frames()) {
    for (const selector of selectors) {
      try {
        const locator = frame.locator(selector).first();
        if (await locator.isVisible()) return locator;
      } catch {
        // A provider frame can disappear while checkout navigates; continue with the other frames.
      }
    }
  }
  return null;
}

async function waitForVisibleInFrames(page, selectors, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  do {
    const locator = await findVisibleInFrames(page, selectors);
    if (locator) return locator;
    if (Date.now() < deadline) await page.waitForTimeout(400);
  } while (Date.now() < deadline);
  return null;
}

function safeDiagnosticValue(value) {
  return String(value || '')
    .replace(/[\\r\\n\\t]+/g, ' ')
    .replace(/\\s+/g, ' ')
    .replace(/\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b/gi, '[email]')
    .replace(/\\b\\d{5,}\\b/g, '[number]')
    .trim()
    .slice(0, 80);
}

async function cashfreeCheckoutDiagnostics(page) {
  const frames = page.frames().slice(0, 8);
  const frameReports = [];
  for (const frame of frames) {
    let host = 'unknown';
    try { host = new URL(frame.url()).hostname; } catch { /* retain safe placeholder */ }
    let rawControls = [];
    try {
      rawControls = await frame.locator('input,button,[role="button"],[role="tab"],label,select')
        .evaluateAll((nodes) => nodes.filter((node) => {
          const style = getComputedStyle(node);
          const rect = node.getBoundingClientRect();
          return style.display !== 'none' && style.visibility !== 'hidden'
            && rect.width > 0 && rect.height > 0;
        }).slice(0, 12).map((node) => ({
          tag: node.tagName.toLowerCase(),
          role: node.getAttribute('role') || '',
          type: node.getAttribute('type') || '',
          name: node.getAttribute('name') || '',
          placeholder: node.getAttribute('placeholder') || '',
          ariaLabel: node.getAttribute('aria-label') || '',
          autocomplete: node.getAttribute('autocomplete') || '',
          text: ['BUTTON', 'LABEL'].includes(node.tagName) ? (node.innerText || '') : '',
        })));
    } catch { /* a frame may navigate while diagnostics are sampled */ }
    frameReports.push({
      host: safeDiagnosticValue(host),
      controls: rawControls.map((control) => ({
        tag: control.tag,
        role: safeDiagnosticValue(control.role),
        type: safeDiagnosticValue(control.type),
        name: safeDiagnosticValue(control.name),
        placeholder: safeDiagnosticValue(control.placeholder),
        ariaLabel: safeDiagnosticValue(control.ariaLabel),
        autocomplete: safeDiagnosticValue(control.autocomplete),
        text: safeDiagnosticValue(control.text),
      })),
    });
  }
  let pageHost = 'unknown';
  try { pageHost = new URL(page.url()).hostname; } catch { /* retain safe placeholder */ }
  return { pageHost: safeDiagnosticValue(pageHost), frameCount: page.frames().length, frames: frameReports };
}

async function mailSlurpRequest(apiKey, method, resource, body, timeoutMs = 30000) {
  let response;
  try {
    response = await fetch('https://api.mailslurp.com' + resource, {
      method,
      headers: {
        'x-api-key': apiKey,
        Accept: 'application/json',
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch {
    throw new QualificationError('Mail inbox service was unreachable or timed out.');
  }
  if (!response.ok) {
    // Never echo response bodies or request headers; they can contain mailbox identifiers.
    throw new QualificationError('Mail inbox service returned HTTP ' + response.status + '.');
  }
  if (response.status === 204) return {};
  try {
    return await response.json();
  } catch {
    throw new QualificationError('Mail inbox service returned an invalid response.');
  }
}

test.describe('automated staging customer payment qualification', () => {
  test.describe.configure({ mode: 'serial', retries: 0 });

  test('complete all eight customer payment-flow checks using a disposable Cashfree sandbox order', async ({ page, browser }) => {
    test.skip(process.env.E2E_PAYMENT_FLOW !== 'true', 'Only the dedicated staging payment-flow workflow runs this stateful test.');
    test.setTimeout(15 * 60 * 1000);
    // Bound locator actions so a stuck browser interaction reports a phase instead of consuming all 15 minutes.
    page.setDefaultTimeout(20_000);
    page.setDefaultNavigationTimeout(45_000);

    const resultPath = process.env.E2E_PAYMENT_RESULT_PATH
      || path.join(os.tmpdir(), 'neelastack-payment-flow-result.json');
    const declaredReleaseSha = String(process.env.E2E_EXPECTED_RELEASE_SHA || '');
    const checks = CHECK_DEFS.map((item) => ({ ...item, status: 'NOT_RUN', detail: 'Not run.' }));
    const report = {
      schemaVersion: 1,
      qualification: 'STAGING_CUSTOMER_PAYMENT_FLOW',
      status: 'INCOMPLETE',
      startedAtUtc: new Date().toISOString(),
      updatedAtUtc: new Date().toISOString(),
      workflowSha: process.env.GITHUB_SHA || null,
      declaredStagingReleaseSha: declaredReleaseSha || null,
      targetOrigin: STAGING_ORIGIN,
      provider: 'CASHFREE_SANDBOX',
      amountMinorUnitsExpected: 10000,
      currencyExpected: 'INR',
      checks,
      blockers: [],
      testStatus: 'PENDING',
      mailboxCleanupStatus: 'PENDING',
      fixtureCleanupStatus: 'PENDING',
      notes: [
        'The reported result describes the protected staging endpoint exercised by this run.',
        'declaredStagingReleaseSha, if supplied, is for traceability only and is not an attested runtime build marker.',
        'Payment/session data, ticket access credentials, customer email, order number and transaction ID are intentionally omitted.',
      ],
    };
    let activeCheck = 'sandbox_payment';
    let activeStep = '';
    let inboxId = '';
    let inboxEmail = '';
    let mailboxCleanupStatus = 'PENDING';
    let flowFailed = false;
    let adminContext = null;

    function recomputeStatus() {
      const hasFailure = report.blockers.length > 0
        || report.testStatus === 'FAIL'
        || mailboxCleanupStatus === 'FAIL'
        || report.fixtureCleanupStatus === 'FAIL'
        || checks.some((item) => item.status === 'FAIL');
      const complete = report.testStatus === 'PASS'
        && mailboxCleanupStatus === 'PASS'
        && report.fixtureCleanupStatus === 'PASS'
        && checks.every((item) => item.status === 'PASS');
      report.status = hasFailure ? 'FAIL' : complete ? 'PASS' : 'INCOMPLETE';
      report.mailboxCleanupStatus = mailboxCleanupStatus;
      report.updatedAtUtc = new Date().toISOString();
    }

    async function saveReport() {
      recomputeStatus();
      try {
        await fs.mkdir(path.dirname(resultPath), { recursive: true });
        await fs.writeFile(resultPath, JSON.stringify(report, null, 2) + '\n', { mode: 0o600 });
      } catch {
        throw new QualificationError('The sanitized machine-readable result could not be written.');
      }
    }

    async function setStep(name) {
      if (activeStep && activeStep !== name) report.lastCompletedStep = activeStep;
      activeStep = name;
      report.currentStep = name;
      report.currentStepStartedAtUtc = new Date().toISOString();
      console.log('[payment-qualification] STEP_START ' + name);
      await saveReport();
    }

    async function markPass(id, detail) {
      const check = checks.find((item) => item.id === id);
      check.status = 'PASS';
      check.detail = detail;
      await saveReport();
    }

    function markActiveFailure(detail) {
      const check = checks.find((item) => item.id === activeCheck);
      if (check && check.status === 'NOT_RUN') {
        check.status = 'FAIL';
        check.detail = detail;
      }
    }

    function providerReturnUrl(url) {
      return url.origin === STAGING_ORIGIN
        && url.pathname === '/payment/success'
        && Boolean(url.searchParams.get('order_id'));
    }

    try {
      await setStep('preflight_and_inbox_setup');
      guard((process.env.E2E_ENV || '').toLowerCase() === 'staging',
        'Preflight blocked: E2E_ENV must be staging.');
      guard(process.env.E2E_BASE_URL === STAGING_ORIGIN,
        'Preflight blocked: the target must be the exact protected staging origin.');
      guard(process.env.E2E_RUN_MUTATIONS === 'true',
        'Preflight blocked: the disposable-fixture mutation gate is not enabled.');
      guard(process.env.E2E_PAYMENT_PROVIDER === 'CASHFREE',
        'Preflight blocked: the generated fixture provider is not Cashfree.');
      guard(Boolean(process.env.E2E_ADMIN_BEARER) && Boolean(process.env.E2E_EVENT_ID)
        && Boolean(process.env.E2E_EVENT_SLUG) && Boolean(process.env.E2E_TICKET_TYPE_ID),
      'Preflight blocked: automatic disposable fixture provisioning did not provide its required references.');
      const mailApiKey = String(process.env.E2E_MAILSLURP_API_KEY || '');
      guard(mailApiKey.length >= 12,
        'Preflight blocked: E2E_MAILSLURP_API_KEY is missing from the protected staging environment.');

      activeCheck = 'ticket_email_references';
      const inboxName = 'neelastack-staging-payment-flow-' + String(process.env.GITHUB_RUN_ID || Date.now());
      const inbox = await mailSlurpRequest(
        mailApiKey,
        'POST',
        '/inboxes?name=' + encodeURIComponent(inboxName) + '&expiresIn=3600000',
        undefined,
      );
      inboxId = String(inbox.id || inbox.inboxId || '');
      inboxEmail = String(inbox.emailAddress || '');
      guard(Boolean(inboxId) && /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(inboxEmail),
        'Could not create a unique disposable email inbox for this qualification run.');

      activeCheck = 'sandbox_payment';
      const eventSlug = String(process.env.E2E_EVENT_SLUG || '');
      guard(/^[a-z0-9][a-z0-9-]{1,100}$/.test(eventSlug),
        'Preflight blocked: the generated disposable event slug is invalid.');

      await setStep('open_disposable_event');
      await page.goto('/events/' + encodeURIComponent(eventSlug), { waitUntil: 'domcontentloaded' });
      const quantityGroup = page.getByRole('group', { name: /Quantity for E2E General/i });
      await setStep('wait_for_ticket_quantity_control');
      try {
        await quantityGroup.waitFor({ state: 'visible', timeout: 30000 });
      } catch {
        throw new QualificationError('The disposable event or its paid ticket type was not bookable.');
      }
      const quantityOutput = quantityGroup.locator('output');
      await setStep('click_increase_ticket_quantity');
      await quantityGroup.getByRole('button', { name: 'Increase' }).click();
      try {
        await expect(quantityOutput).toHaveText('1', { timeout: 10000 });
      } catch {
        throw new QualificationError('Ticket quantity stayed at zero after the Increase click; the event page may not be interactive yet.');
      }

      const continueButton = page.getByRole('button', { name: /Continue to checkout/i }).first();
      await setStep('wait_for_checkout_button_enabled');
      try {
        await expect(continueButton).toBeEnabled({ timeout: 10000 });
      } catch {
        throw new QualificationError('Ticket selection did not enable Continue to checkout; no checkout request was started.');
      }

      await setStep('click_continue_to_checkout');
      await continueButton.click();
      await setStep('wait_for_checkout_route');
      try {
        await page.waitForURL((url) => url.origin === STAGING_ORIGIN && url.pathname === '/checkout', { timeout: 20000 });
      } catch {
        throw new QualificationError('The customer journey did not reach the checkout page.');
      }

      await setStep('fill_customer_name');
      await page.locator('#customerName').fill('Neelastack Payment Qualification');
      await setStep('fill_disposable_customer_email');
      await page.locator('#customerEmail').fill(inboxEmail);
      await setStep('fill_customer_phone');
      await page.locator('#customerPhone').fill('9876543210');
      await setStep('accept_checkout_terms');
      await page.locator('input[formcontrolname="accepted"]').check();

      await setStep('click_pay_securely');
      const cashfreeNavigation = page.waitForURL((url) => sandboxCashfreeHost(url.hostname), { timeout: 60000 })
        .then(() => true)
        .catch(() => false);
      await page.getByRole('button', { name: /Pay securely/i }).click();
      await setStep('wait_for_cashfree_sandbox_redirect');
      guard(await cashfreeNavigation, 'Cashfree sandbox checkout did not open; confirm the deployed provider is configured in sandbox mode.');

      await setStep('locate_cashfree_upi_controls');
      const upiInputSelectors = [
        'input[placeholder*="upi" i]',
        'input[placeholder*="vpa" i]',
        'input[placeholder*="virtual payment" i]',
        'input[aria-label*="upi" i]',
        'input[aria-label*="vpa" i]',
        'input[aria-label*="virtual payment" i]',
        'input[name*="upi" i]',
        'input[name*="vpa" i]',
        'input[id*="upi" i]',
        'input[id*="vpa" i]',
        'input[data-testid*="upi" i]',
        'input[autocomplete*="upi" i]',
        '[role="textbox"][aria-label*="upi" i]',
      ];
      const upiChoiceSelectors = [
        'button:has-text("UPI")',
        '[role="button"]:has-text("UPI")',
        '[role="tab"]:has-text("UPI")',
        'label:has-text("UPI")',
        'button[aria-label*="UPI" i]',
        '[role="button"][aria-label*="UPI" i]',
      ];

      // Cashfree hosted checkout can finish its redirect before its payment-method UI is
      // rendered. Poll rather than failing on a one-shot DOM sample. Search every currently
      // attached frame on each pass because the hosted checkout may add frames dynamically.
      let upiInput = await findVisibleInFrames(page, upiInputSelectors);
      let upiChoiceAttempted = false;
      let controlsDeadline = Date.now() + 20000;
      while (!upiInput && Date.now() < controlsDeadline) {
        if (!upiChoiceAttempted) {
          const upiChoice = await findVisibleInFrames(page, upiChoiceSelectors);
          if (upiChoice) {
            upiChoiceAttempted = true;
            await setStep('select_cashfree_upi_method');
            try {
              await upiChoice.click({ timeout: 5000 });
            } catch {
              // Continue to check whether the provider exposed the entry control despite the click.
            }
            controlsDeadline = Date.now() + 15000;
          }
        }
        upiInput = await findVisibleInFrames(page, upiInputSelectors);
        if (!upiInput) await page.waitForTimeout(400);
      }
      if (!upiInput) {
        // This report contains only frame hostnames and visible control metadata. It deliberately
        // excludes field values, cookies, URLs with query strings, response bodies and screenshots.
        report.cashfreeCheckoutDiagnostics = await cashfreeCheckoutDiagnostics(page);
        await saveReport();
        throw new QualificationError(
          'Cashfree sandbox checkout did not expose a supported UPI-ID input after waiting for the hosted UI; sanitized control diagnostics were recorded.'
        );
      }
      await setStep('enter_cashfree_sandbox_upi');
      await upiInput.fill('testsuccess@gocash');

      await setStep('submit_cashfree_sandbox_payment');
      const verifyButton = await findVisibleInFrames(page, [
        'button:has-text("Verify")',
        'button:has-text("Verify & Pay")',
        'button:has-text("Verify and Pay")',
      ]);
      if (verifyButton) {
        await setStep('click_cashfree_upi_verify');
        await verifyButton.click();
        const earlyReturn = page.waitForURL((url) => providerReturnUrl(url), { timeout: 8000 })
          .then(() => true)
          .catch(() => false);
        if (!(await earlyReturn)) {
          const payButton = await findVisibleInFrames(page, [
            'button:has-text("Pay Now")',
            'button:has-text("Continue")',
            'button:has-text("Proceed")',
            'button:has-text("Pay")',
            'button:has-text("Confirm")',
          ]);
          guard(Boolean(payButton), 'Cashfree sandbox UPI verification completed without a visible payment action.');
          await setStep('click_cashfree_payment_action');
          await payButton.click();
        }
      } else {
        const payButton = await findVisibleInFrames(page, [
          'button:has-text("Pay Now")',
          'button:has-text("Continue")',
          'button:has-text("Proceed")',
          'button:has-text("Pay")',
          'button:has-text("Confirm")',
        ]);
        guard(Boolean(payButton), 'Cashfree sandbox checkout did not expose a supported payment action.');
        await setStep('click_cashfree_payment_action');
        await payButton.click();
      }

      await setStep('wait_for_staging_payment_return');
      try {
        await page.waitForURL((url) => providerReturnUrl(url), { timeout: 120000 });
      } catch {
        throw new QualificationError('Cashfree sandbox did not return the customer to the payment result page after the test payment.');
      }
      await setStep('wait_for_server_payment_confirmation');
      try {
        await page.getByText('Payment confirmed', { exact: true }).waitFor({ state: 'visible', timeout: 90000 });
      } catch {
        throw new QualificationError('The server-side payment verification did not reach the customer-visible confirmed state.');
      }

      const orderChip = page.locator('.order-chip').filter({ hasText: 'NEELASTACK ORDER NUMBER' });
      const orderLabelVisible = await orderChip.isVisible().catch(() => false);
      const orderNumber = (await orderChip.locator('strong').innerText().catch(() => '')).trim();
      guard(orderLabelVisible && Boolean(orderNumber),
        'The confirmed payment result did not display a Neelastack order number.');
      report.facts = { orderNumberDisplayed: true };
      await markPass('sandbox_payment', 'Cashfree sandbox returned and the application displayed a confirmed payment.');
      await markPass('order_number_display', 'The confirmed payment result displays a non-empty Neelastack order number.');

      activeCheck = 'replay_safety';
      await setStep('repeat_verification_and_check_replay_safety');
      const providerOrderId = new URL(page.url()).searchParams.get('order_id') || '';
      guard(Boolean(providerOrderId), 'The payment result did not retain a provider order reference for verification.');
      const verifyUrl = STAGING_ORIGIN + '/api/v1/public/checkout/verify';
      const authorisedResults = [];
      for (let attempt = 0; attempt < 2; attempt += 1) {
        const response = await page.request.post(verifyUrl, { data: { providerOrderId } });
        guard(response.status() === 200, 'Authenticated repeat verification returned an unexpected HTTP status.');
        let body;
        try { body = await response.json(); } catch { throw new QualificationError('Authenticated repeat verification returned invalid JSON.'); }
        authorisedResults.push(body);
      }
      const first = authorisedResults[0];
      const second = authorisedResults[1];
      const ticketIdsFromVerify = (value) => Array.isArray(value && value.tickets)
        ? value.tickets.map((ticket) => String(ticket && ticket.ticketId || '')).filter(Boolean)
        : [];
      const repeatedIdsA = ticketIdsFromVerify(first);
      const repeatedIdsB = ticketIdsFromVerify(second);
      const confirmedA = ['CONFIRMED', 'COMPLETED'].includes(String(first.status || '').toUpperCase());
      const confirmedB = ['CONFIRMED', 'COMPLETED'].includes(String(second.status || '').toUpperCase());
      guard(confirmedA && confirmedB && first.orderNumber === orderNumber && second.orderNumber === orderNumber,
        'Authenticated repeat verification did not return the same confirmed order.');
      guard(repeatedIdsA.length === 1 && repeatedIdsB.length === 1
        && repeatedIdsA[0] === repeatedIdsB[0],
      'Repeated verification did not return exactly the same single ticket reference.');

      const uiTicketHrefs = await page.locator('.ticket-row a[href*="/ticket/"]').evaluateAll((nodes) => nodes
        .map((node) => node.getAttribute('href') || '')
        .map((href) => {
          const match = href.match(/\/ticket\/([^/?#]+)/);
          return match ? match[1] : '';
        })
        .filter(Boolean));
      guard(uiTicketHrefs.length === 1 && uiTicketHrefs[0] === repeatedIdsA[0],
        'The confirmed result page and backend disagree about the single ticket issued.');

      const uiTransactionChip = page.locator('.order-chip').filter({ hasText: 'PAYMENT TRANSACTION ID' });
      const displayedTransactionId = (await uiTransactionChip.locator('strong').innerText().catch(() => '')).trim();
      const verifiedTransactionId = String(first.transactionId || '').trim();
      guard(Boolean(displayedTransactionId) && Boolean(verifiedTransactionId)
        && displayedTransactionId === verifiedTransactionId
        && String(second.transactionId || '').trim() === verifiedTransactionId,
      'The customer-visible transaction reference did not match the provider reference returned by verification.');
      report.facts.transactionIdDisplayedAndMatched = true;
      await markPass('transaction_id_display', 'The displayed provider transaction ID matches repeated server-side payment verification.');

      const anonymousContext = await browser.newContext({ baseURL: STAGING_ORIGIN });
      try {
        const response = await anonymousContext.request.post(verifyUrl, { data: { providerOrderId } });
        guard(response.status() === 200, 'Anonymous replay did not return the expected status-only response.');
        let anonymousBody;
        try { anonymousBody = await response.json(); } catch { throw new QualificationError('Anonymous replay returned invalid JSON.'); }
        guard(['CONFIRMED', 'COMPLETED'].includes(String(anonymousBody.status || '').toUpperCase())
          && anonymousBody.orderNumber === orderNumber
          && Array.isArray(anonymousBody.tickets) && anonymousBody.tickets.length === 0
          && (anonymousBody.transactionId === null || anonymousBody.transactionId === undefined)
          && !hasCredentialFields(anonymousBody),
        'Anonymous replay did not stay status-only or returned ticket access credentials.');
      } finally {
        await anonymousContext.close().catch(() => {});
      }
      await markPass('replay_safety', 'Two authenticated retries are stable; an anonymous replay receives status only, with no ticket credentials.');

      activeCheck = 'exactly_one_ticket';
      await setStep('verify_exactly_one_ticket_in_admin');
      const adminBearer = String(process.env.E2E_ADMIN_BEARER || '').replace(/^Bearer\s+/i, '').trim();
      adminContext = await browser.newContext({ baseURL: STAGING_ORIGIN });
      const adminResponse = await adminContext.request.get(
        STAGING_ORIGIN + '/api/v1/admin/events/' + encodeURIComponent(process.env.E2E_EVENT_ID)
          + '/orders?q=' + encodeURIComponent(orderNumber) + '&page=0&size=50',
        { headers: { Authorization: 'Bearer ' + adminBearer } },
      );
      guard(adminResponse.status() === 200, 'The staging admin order query could not verify issued ticket count.');
      let adminOrdersBody;
      try { adminOrdersBody = await adminResponse.json(); } catch { throw new QualificationError('The staging admin order query returned invalid JSON.'); }
      const orderRows = Array.isArray(adminOrdersBody.items) ? adminOrdersBody.items
        : Array.isArray(adminOrdersBody.rows) ? adminOrdersBody.rows : [];
      const matchingOrders = orderRows.filter((item) => item && item.orderNumber === orderNumber);
      guard(matchingOrders.length === 1 && Number(matchingOrders[0].ticketCount) === 1
        && ['CONFIRMED', 'COMPLETED'].includes(String(matchingOrders[0].status || '').toUpperCase())
        && ['CAPTURED', 'COMPLETED'].includes(String(matchingOrders[0].paymentStatus || '').toUpperCase()),
      'The server-side order/payment records do not confirm payment with exactly one issued ticket.');
      report.facts.issuedTicketCount = 1;
      await markPass('exactly_one_ticket', 'Customer result and authenticated admin order records both show exactly one issued ticket.');

      activeCheck = 'ticket_email_references';
      await setStep('verify_ticket_email_references');
      const mail = await mailSlurpRequest(
        mailApiKey,
        'GET',
        '/waitForLatestEmail?inboxId=' + encodeURIComponent(inboxId) + '&timeout=180000&unreadOnly=true',
        undefined,
        210000,
      );
      let emailDetails = mail;
      const mailBody = [mail.body, mail.bodyExcerpt, mail.textBody, mail.htmlBody].filter(Boolean).join('\n');
      if (!mailBody.includes(orderNumber)) {
        const emailId = String(mail.id || mail.emailId || '');
        guard(Boolean(emailId), 'The received ticket email had no body or retrievable message reference.');
        emailDetails = await mailSlurpRequest(mailApiKey, 'GET', '/emails/' + encodeURIComponent(emailId), undefined);
      }
      const completeMailBody = [
        emailDetails.body, emailDetails.bodyExcerpt, emailDetails.textBody, emailDetails.htmlBody,
      ].filter(Boolean).join('\n');
      guard(completeMailBody.includes(orderNumber) && completeMailBody.includes(verifiedTransactionId),
        'The received ticket email did not contain both the order number and verified provider transaction ID.');
      report.facts.ticketEmailReferencesPresent = true;
      await markPass('ticket_email_references', 'A real received ticket email contains both verified customer payment references.');

      activeCheck = 'same_email_recovery';
      await setStep('verify_same_email_recovery');
      await page.goto(STAGING_ORIGIN + '/recover', { waitUntil: 'domcontentloaded' });
      await page.locator('#orderNumber').fill(orderNumber);
      await page.locator('#email').fill(inboxEmail);
      await page.getByRole('button', { name: /Recover tickets/i }).click();
      try {
        await page.locator('.found').waitFor({ state: 'visible', timeout: 30000 });
      } catch {
        throw new QualificationError('Ticket recovery did not return a result when using the original checkout email.');
      }
      const recoveryText = await page.locator('.found').innerText().catch(() => '');
      const recoveryTicketHrefs = await page.locator('.found a[href*="/ticket/"]').evaluateAll((nodes) => nodes
        .map((node) => node.getAttribute('href') || '')
        .map((href) => {
          const match = href.match(/\/ticket\/([^/?#]+)/);
          return match ? match[1] : '';
        })
        .filter(Boolean));
      guard(recoveryText.includes(orderNumber) && recoveryText.includes(verifiedTransactionId)
        && recoveryTicketHrefs.length === 1 && recoveryTicketHrefs[0] === repeatedIdsA[0],
      'Ticket recovery with the checkout email did not return the same order, transaction reference and single ticket.');
      await markPass('same_email_recovery', 'Recovery using the exact checkout email returns the same order, transaction ID and ticket.');

      activeCheck = 'ledger_reconciliation';
      await setStep('verify_ledger_reconciliation');
      const ledgerResponse = await adminContext.request.get(
        STAGING_ORIGIN + '/api/v1/finance/ledger?q=' + encodeURIComponent(orderNumber)
          + '&entryType=SALE&page=0&size=100',
        { headers: { Authorization: 'Bearer ' + adminBearer } },
      );
      guard(ledgerResponse.status() === 200, 'The authenticated staging ledger query returned an unexpected HTTP status.');
      let ledgerBody;
      try { ledgerBody = await ledgerResponse.json(); } catch { throw new QualificationError('The staging ledger query returned invalid JSON.'); }
      const ledgerRows = Array.isArray(ledgerBody.items) ? ledgerBody.items : [];
      const matchingSales = ledgerRows.filter((item) => item && item.orderNumber === orderNumber && item.entryType === 'SALE');
      guard(matchingSales.length === 1
        && Number(matchingSales[0].amountMinor) === 10000
        && String(matchingSales[0].currency || '').toUpperCase() === 'INR',
      'The ledger does not contain exactly one INR 100.00 SALE record for the confirmed order.');
      report.facts.saleLedgerCount = 1;
      report.facts.ledgerAmountMatched = true;
      await markPass('ledger_reconciliation', 'Exactly one matching SALE ledger row reconciles to the INR 100.00 paid order.');
      report.lastCompletedStep = activeStep;
      report.currentStep = null;
      report.currentStepStartedAtUtc = null;
      console.log('[payment-qualification] STEP_COMPLETE ' + activeStep);
      await saveReport();
    } catch (error) {
      flowFailed = true;
      const timeoutLike = Boolean(error && (error.name === 'TimeoutError'
        || /timeout of \d+ms exceeded|timed out/i.test(String(error.message || ''))));
      const safeMessage = error instanceof QualificationError
        ? error.message
        : timeoutLike
          ? 'Browser/test timeout during qualification step: ' + (activeStep || 'unknown') + '.'
          : 'Unexpected error during qualification step: ' + (activeStep || 'unknown') + '; sensitive diagnostics suppressed.';
      markActiveFailure(safeMessage);
      report.blockers.push(safeMessage);
      report.testStatus = 'FAIL';
    } finally {
      if (adminContext) await adminContext.close().catch(() => {});
      if (inboxId) {
        try {
          await mailSlurpRequest(
            String(process.env.E2E_MAILSLURP_API_KEY || ''),
            'DELETE',
            '/inboxes/' + encodeURIComponent(inboxId),
            undefined,
          );
          mailboxCleanupStatus = 'PASS';
        } catch {
          mailboxCleanupStatus = 'FAIL';
          report.blockers.push('The temporary qualification inbox could not be deleted.');
          flowFailed = true;
          report.testStatus = 'FAIL';
        }
      } else {
        mailboxCleanupStatus = 'NOT_REQUIRED';
      }
      if (!flowFailed && checks.every((item) => item.status === 'PASS')) report.testStatus = 'PASS';
      else if (report.testStatus !== 'FAIL') report.testStatus = 'FAIL';
      await saveReport();
    }

    if (flowFailed || report.testStatus !== 'PASS') {
      throw new Error('Automated staging payment-flow qualification failed. Review the sanitized payment-flow result artifact.');
    }
  });
});
