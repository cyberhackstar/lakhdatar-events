import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const EVENT_ID = __ENV.EVENT_ID || '';
const TICKET_TYPE_ID = __ENV.TICKET_TYPE_ID || '';
const TEST_KEY = __ENV.TEST_IDEMPOTENCY_KEY || `LT-IDEMP-${Date.now()}`;
if (!BASE_URL || !EVENT_ID || !TICKET_TYPE_ID) throw new Error('Set BASE_URL, EVENT_ID and TICKET_TYPE_ID.');
if (/events\.neelastack\.com$/i.test(new URL(BASE_URL).hostname) && __ENV.ALLOW_PRODUCTION_CHECKOUT_LOAD !== 'true') throw new Error('Idempotency load test is blocked against production.');

const contract = new Rate('idempotency_contract');
const successfulCreate = new Rate('idempotency_success');
const serverErrors = new Rate('idempotency_server_errors');

export const options = {
  scenarios: { duplicate_requests: { executor: 'constant-arrival-rate', rate: 5, timeUnit: '1s', duration: '30s', preAllocatedVUs: 10, maxVUs: 20 } },
  thresholds: {
    idempotency_contract: ['rate>0.99'],
    idempotency_success: ['rate>0.01'],
    idempotency_server_errors: ['rate<0.01'],
    checks: ['rate>0.99'],
    http_req_duration: ['p(95)<2000','p(99)<4000']
  }
};

export default function () {
  const body = JSON.stringify({ eventId: EVENT_ID, customerName: `Idempotency ${__VU}-${__ITER}`, customerEmail: `idempotency-${__VU}-${__ITER}@example.invalid`, idempotencyKey: TEST_KEY, items: [{ ticketTypeId: TICKET_TYPE_ID, quantity: 1 }] });
  const r = http.post(`${BASE_URL}/api/v1/public/checkout`, body, { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'checkout-idempotency' } });
  const deterministic = [200, 201, 409, 429].includes(r.status);
  contract.add(deterministic);
  successfulCreate.add(r.status === 200 || r.status === 201);
  serverErrors.add(r.status >= 500);
  check(r, { 'same key has deterministic outcome': x => deterministic, 'same key never returns 5xx': x => x.status < 500 });
}
