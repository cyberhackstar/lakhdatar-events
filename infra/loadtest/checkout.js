import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const EVENT_ID = __ENV.EVENT_ID || '';
const TICKET_TYPE_ID = __ENV.TICKET_TYPE_ID || '';
const CUSTOMER_PREFIX = __ENV.CUSTOMER_PREFIX || 'Load Test';
const ENABLE = __ENV.ENABLE_CHECKOUT_LOAD === 'true';

if (!BASE_URL || !EVENT_ID || !TICKET_TYPE_ID) throw new Error('BASE_URL, EVENT_ID and TICKET_TYPE_ID are required.');
// Parse the host from an absolute HTTP(S) URL without relying on Node/browser URL globals.
// k6 runs these scripts from stdin in a small JS runtime, so the browser/Node URL constructor is not portable here.
const BASE_URL_HOST_MATCH = /^https?:\/\/(\[[0-9a-f:.]+\]|[^/:?#@]+)(?::\d+)?(?:[/?#]|$)/i.exec(BASE_URL);
if (!BASE_URL_HOST_MATCH) throw new Error('BASE_URL must be an absolute HTTP(S) URL without embedded credentials.');
const BASE_URL_HOST = BASE_URL_HOST_MATCH[1].replace(/^\[|\]$/g, '').toLowerCase();
const IS_PRODUCTION_EVENTS_HOST = BASE_URL_HOST === 'events.neelastack.com' || BASE_URL_HOST.endsWith('.events.neelastack.com');
if (!ENABLE) throw new Error('Refusing checkout load test: set ENABLE_CHECKOUT_LOAD=true explicitly.');
if (IS_PRODUCTION_EVENTS_HOST && __ENV.ALLOW_PRODUCTION_CHECKOUT_LOAD !== 'true') {
  throw new Error('Checkout load testing is blocked against production. Use a dedicated staging/test event/provider sandbox.');
}

const responseContract = new Rate('checkout_contract');
const success = new Rate('checkout_success');
const serverErrors = new Rate('checkout_server_errors');
let diagnosticFailures = 0;

// Print only a strict machine-readable API error code and timing. Never print response bodies,
// cookies, headers, payment identifiers, or customer fields from staging responses.
function safeErrorCode(response) {
  try {
    const payload = response.json();
    const candidate = payload && (payload.code || payload.errorCode || payload.error_code);
    return typeof candidate === 'string' && /^[A-Za-z0-9_.-]{1,80}$/.test(candidate)
      ? candidate : 'unavailable';
  } catch (_) {
    return 'unavailable';
  }
}

export const options = {
  scenarios: {
    checkout_provisioning: {
      // Keep the success-path checkout probe below the default per-client limit of 20/minute.
      // Higher-rate overload behavior is covered separately by checkout-hot-sale.js, where 429
      // is an explicit rate-limit outcome rather than a successful checkout.
      executor: 'constant-arrival-rate', rate: 1, timeUnit: '6s', duration: '4m',
      preAllocatedVUs: 5, maxVUs: 30, gracefulStop: '30s'
    }
  },
  thresholds: {
    checkout_contract: ['rate>0.99'],
    checkout_success: ['rate>0.90'],
    checkout_server_errors: ['rate<0.01'],
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<2000', 'p(99)<4000']
  }
};

export default function () {
  const email = `loadtest-${__VU}-${__ITER}@example.com`;
  const body = JSON.stringify({
    eventId: EVENT_ID,
    customerName: `${CUSTOMER_PREFIX} ${__VU}-${__ITER}`,
    customerEmail: email,
    customerPhone: '9876543210',
    idempotencyKey: `LT-${Date.now()}-${__VU}-${__ITER}-${Math.floor(Math.random()*1_000_000_000)}`,
    items: [{ ticketTypeId: TICKET_TYPE_ID, quantity: 1 }]
  });
  const res = http.post(`${BASE_URL}/api/v1/public/checkout`, body, { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'checkout-provision' } });
  const contract = [200, 201, 409, 429].includes(res.status);
  responseContract.add(contract);
  success.add(res.status === 200 || res.status === 201);
  serverErrors.add(res.status >= 500);
  if ((!contract || res.status >= 500) && diagnosticFailures < 3) {
    diagnosticFailures += 1;
    console.error(`[checkout-load] unexpected HTTP status=${res.status} apiCode=${safeErrorCode(res)} durationMs=${Math.round(res.timings.duration)}; response body and credentials omitted`);
  }
  check(res, { 'checkout response contract': r => contract, 'checkout has no server error': r => r.status < 500 });
}
