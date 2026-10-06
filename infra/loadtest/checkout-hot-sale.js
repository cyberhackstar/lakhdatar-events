import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

// Staging-only hot-sale qualification. This intentionally exercises the expensive checkout path,
// not just public reads. Use a dedicated test event and payment sandbox.
const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const EVENT_ID = __ENV.EVENT_ID || '';
const TICKET_TYPE_ID = __ENV.TICKET_TYPE_ID || '';
const ENABLE = __ENV.ENABLE_ENTERPRISE_CHECKOUT_LOAD === 'true';
const TARGET = Number(__ENV.CHECKOUT_TARGET_RATE || 100);

if (!BASE_URL || !EVENT_ID || !TICKET_TYPE_ID) throw new Error('BASE_URL, EVENT_ID and TICKET_TYPE_ID are required.');
if (!ENABLE) throw new Error('Refusing enterprise checkout load: set ENABLE_ENTERPRISE_CHECKOUT_LOAD=true explicitly.');
const host = new URL(BASE_URL).hostname;
if (/events\.neelastack\.com$/i.test(host) && __ENV.ALLOW_PRODUCTION_CHECKOUT_LOAD !== 'true') {
  throw new Error('Enterprise checkout load is blocked against production. Use staging with a payment sandbox.');
}

const contract = new Rate('checkout_contract');
const serverErrors = new Rate('checkout_server_errors');

export const options = {
  scenarios: {
    hot_sale: {
      executor: 'ramping-arrival-rate', startRate: Math.max(10, Math.floor(TARGET * 0.1)), timeUnit: '1s',
      preAllocatedVUs: Math.min(200, Math.max(50, TARGET)), maxVUs: Math.min(1500, Math.max(200, TARGET * 12)),
      stages: [
        { target: Math.max(25, Math.floor(TARGET * 0.25)), duration: '1m' },
        { target: Math.max(50, Math.floor(TARGET * 0.50)), duration: '1m' },
        { target: TARGET, duration: '2m' },
        { target: TARGET, duration: '3m' },
        { target: 0, duration: '1m' },
      ],
      gracefulStop: '1m',
    },
  },
  thresholds: {
    checkout_contract: ['rate>0.99'],
    checkout_server_errors: ['rate<0.01'],
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<3000', 'p(99)<5000'],
  },
};

export default function () {
  const id = `${Date.now()}-${__VU}-${__ITER}-${Math.floor(Math.random() * 1e9)}`;
  const body = JSON.stringify({
    eventId: EVENT_ID,
    customerName: `Enterprise Load ${__VU}-${__ITER}`,
    customerEmail: `enterprise-load-${id}@example.invalid`,
    customerPhone: null,
    idempotencyKey: `ENT-${id}`,
    items: [{ ticketTypeId: TICKET_TYPE_ID, quantity: 1 }],
  });
  const response = http.post(`${BASE_URL}/api/v1/public/checkout`, body, {
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    tags: { scenario: 'enterprise-hot-sale' },
  });
  const ok = [200, 201, 409, 429, 503].includes(response.status);
  contract.add(ok);
  serverErrors.add(response.status >= 500 && response.status !== 503);
  check(response, { 'checkout has an explicit overload/provider response': r => ok });
}
