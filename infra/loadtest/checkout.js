import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const EVENT_ID = __ENV.EVENT_ID || '';
const TICKET_TYPE_ID = __ENV.TICKET_TYPE_ID || '';
const CUSTOMER_PREFIX = __ENV.CUSTOMER_PREFIX || 'Load Test';
const ENABLE = __ENV.ENABLE_CHECKOUT_LOAD === 'true';

if (!BASE_URL || !EVENT_ID || !TICKET_TYPE_ID) throw new Error('BASE_URL, EVENT_ID and TICKET_TYPE_ID are required.');
if (!ENABLE) throw new Error('Refusing checkout load test: set ENABLE_CHECKOUT_LOAD=true explicitly.');
if (/events\.neelastack\.com$/i.test(new URL(BASE_URL).hostname) && __ENV.ALLOW_PRODUCTION_CHECKOUT_LOAD !== 'true') {
  throw new Error('Checkout load testing is blocked against production. Use a dedicated staging/test event/provider sandbox.');
}

const responseContract = new Rate('checkout_contract');
const success = new Rate('checkout_success');
const serverErrors = new Rate('checkout_server_errors');

export const options = {
  scenarios: {
    checkout_provisioning: {
      executor: 'ramping-arrival-rate', startRate: 1, timeUnit: '1s', preAllocatedVUs: 5, maxVUs: 30,
      stages: [
        { target: 1, duration: '30s' },
        { target: 2, duration: '60s' },
        { target: 5, duration: '60s' },
        { target: 10, duration: '60s' },
        { target: 2, duration: '30s' }
      ], gracefulStop: '30s'
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
  const email = `loadtest-${__VU}-${__ITER}@example.invalid`;
  const body = JSON.stringify({
    eventId: EVENT_ID,
    customerName: `${CUSTOMER_PREFIX} ${__VU}-${__ITER}`,
    customerEmail: email,
    customerPhone: null,
    idempotencyKey: `LT-${Date.now()}-${__VU}-${__ITER}-${Math.floor(Math.random()*1_000_000_000)}`,
    items: [{ ticketTypeId: TICKET_TYPE_ID, quantity: 1 }]
  });
  const res = http.post(`${BASE_URL}/api/v1/public/checkout`, body, { headers: { 'Content-Type': 'application/json' }, tags: { endpoint: 'checkout-provision' } });
  const contract = [200, 201, 409, 429].includes(res.status);
  responseContract.add(contract);
  success.add(res.status === 200 || res.status === 201);
  serverErrors.add(res.status >= 500);
  check(res, { 'checkout response contract': r => contract, 'checkout has no server error': r => r.status < 500 });
}
