import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const EVENT_ID = __ENV.EVENT_ID || '';
const TICKET_TYPE_ID = __ENV.TICKET_TYPE_ID || '';
const TEST_KEY = __ENV.TEST_IDEMPOTENCY_KEY || `LT-IDEMP-${Date.now()}`;
if (!BASE_URL || !EVENT_ID || !TICKET_TYPE_ID) throw new Error('Set BASE_URL, EVENT_ID and TICKET_TYPE_ID.');
// Parse the host from an absolute HTTP(S) URL without relying on Node/browser URL globals.
// k6 runs these scripts from stdin in a small JS runtime, so the browser/Node URL constructor is not portable here.
const BASE_URL_HOST_MATCH = /^https?:\/\/(\[[0-9a-f:.]+\]|[^/:?#@]+)(?::\d+)?(?:[/?#]|$)/i.exec(BASE_URL);
if (!BASE_URL_HOST_MATCH) throw new Error('BASE_URL must be an absolute HTTP(S) URL without embedded credentials.');
const BASE_URL_HOST = BASE_URL_HOST_MATCH[1].replace(/^\[|\]$/g, '').toLowerCase();
const IS_PRODUCTION_EVENTS_HOST = BASE_URL_HOST === 'events.neelastack.com' || BASE_URL_HOST.endsWith('.events.neelastack.com');
if (IS_PRODUCTION_EVENTS_HOST && __ENV.ALLOW_PRODUCTION_CHECKOUT_LOAD !== 'true') throw new Error('Idempotency load test is blocked against production.');

const contract = new Rate('idempotency_contract');
const successfulCreate = new Rate('idempotency_success');
const serverErrors = new Rate('idempotency_server_errors');

export const options = {
  // Four concurrent retries are enough to exercise idempotency races while staying below the
  // backend's minimum per-email checkout limiter (5 requests/window). Never turn this into a
  // long high-rate loop: that tests rate limiting, not idempotency.
  scenarios: { duplicate_requests: { executor: 'constant-arrival-rate', rate: 4, timeUnit: '1s', duration: '1s', preAllocatedVUs: 10, maxVUs: 20 } },
  thresholds: {
    idempotency_contract: ['rate>0.99'],
    idempotency_success: ['rate>0.01'],
    idempotency_server_errors: ['rate<0.01'],
    checks: ['rate>0.99'],
    http_req_duration: ['p(95)<2000','p(99)<4000']
  }
};

export default function () {
  // Idempotency means the same key AND the same checkout payload on every retry. Changing
  // customer fields with a reused key correctly triggers IDEMPOTENCY_CONFLICT (409).
  const body = JSON.stringify({ eventId: EVENT_ID, customerName: 'Idempotency Load Test', customerEmail: 'idempotency-load@example.invalid', customerPhone: null, idempotencyKey: TEST_KEY, items: [{ ticketTypeId: TICKET_TYPE_ID, quantity: 1 }] });
  const r = http.post(`${BASE_URL}/api/v1/public/checkout`, body, { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'checkout-idempotency' } });
  const deterministic = [200, 201, 409, 429].includes(r.status);
  contract.add(deterministic);
  successfulCreate.add(r.status === 200 || r.status === 201);
  serverErrors.add(r.status >= 500);
  check(r, { 'same key has deterministic outcome': x => deterministic, 'same key never returns 5xx': x => x.status < 500 });
}
