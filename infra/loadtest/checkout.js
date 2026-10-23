import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { sleep } from 'k6';

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
const initialTransportFailures = new Rate('checkout_initial_transport_failures');
const finalTransportFailures = new Rate('checkout_final_transport_failures');
const finalFailures = new Rate('checkout_final_failures');
const retryUsed = new Rate('checkout_transport_retry_used');
const logicalDuration = new Trend('checkout_logical_duration_ms');
let diagnosticFailures = 0;
let recoveredTransportDiagnostics = 0;

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

// HTTP 409/429 are explicit checkout outcomes in this scenario, not transport failures.
// A 0 status has no HTTP response; the caller may have lost the response after the API handled
// the idempotency key. In that case only, one replay with the exact same body/key is safe.
http.setResponseCallback(http.expectedStatuses(200, 201, 409, 429));

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
    checkout_initial_transport_failures: ['rate<0.03'],
    checkout_transport_retry_used: ['rate<0.03'],
    checkout_final_transport_failures: ['rate<0.01'],
    checkout_final_failures: ['rate<0.01'],
    checkout_logical_duration_ms: ['p(95)<2500', 'p(99)<4500'],
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
  const url = `${BASE_URL}/api/v1/public/checkout`;
  const headers = { 'Content-Type': 'application/json', Accept: 'application/json' };
  const startedAt = Date.now();
  let res = http.post(url, body, { headers, tags: { endpoint: 'checkout-provision', attempt: 'initial' } });
  const initialTransportFailure = res.status === 0;
  initialTransportFailures.add(initialTransportFailure);
  retryUsed.add(initialTransportFailure);

  // Retry only when no HTTP response arrived (e.g. an edge TCP reset). Reuse the identical
  // request body and idempotency key; never retry HTTP 4xx/5xx/provider responses here.
  if (initialTransportFailure) {
    sleep(0.25);
    res = http.post(url, body, { headers, tags: { endpoint: 'checkout-provision', attempt: 'transport-retry' } });
  }

  const logicalDurationMs = Date.now() - startedAt;
  const contract = [200, 201, 409, 429].includes(res.status);
  const finalFailure = !contract || res.status >= 500;
  responseContract.add(contract);
  success.add(res.status === 200 || res.status === 201);
  serverErrors.add(res.status >= 500);
  finalTransportFailures.add(res.status === 0);
  finalFailures.add(finalFailure);
  logicalDuration.add(logicalDurationMs);

  if (initialTransportFailure && !finalFailure && recoveredTransportDiagnostics < 3) {
    recoveredTransportDiagnostics += 1;
    console.warn(`[checkout-load] recovered no-response transport failure with one same-key retry; initialStatus=0 finalStatus=${res.status} logicalDurationMs=${logicalDurationMs}; response body and credentials omitted`);
  }
  if (finalFailure && diagnosticFailures < 3) {
    diagnosticFailures += 1;
    console.error(`[checkout-load] final checkout failure after ${initialTransportFailure ? 'transport retry' : 'single attempt'}; HTTP status=${res.status} apiCode=${safeErrorCode(res)} logicalDurationMs=${logicalDurationMs}; response body and credentials omitted`);
  }
  check(res, {
    'checkout response contract': () => contract,
    'checkout has no server error': r => r.status < 500,
    'checkout final response is not a transport failure': r => r.status !== 0,
  });
}
