import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const EVENT_ID = __ENV.ADMIN_EVENT_ID || '';
const BEARER = __ENV.ADMIN_BEARER || '';
const OPERATIONS_RATE = Number(__ENV.OPERATIONS_RATE || 4);
if (!BASE_URL || !EVENT_ID || !BEARER) fail('BASE_URL, ADMIN_EVENT_ID and ADMIN_BEARER are required.');
if (!Number.isInteger(OPERATIONS_RATE) || OPERATIONS_RATE < 1 || OPERATIONS_RATE > 4) {
  fail('OPERATIONS_RATE must be an integer from 1 to 4 iterations/s; each iteration issues four requests to the shared 20 RPS edge API limit.');
}

const rateLimitedResponses = new Counter('operations_rate_limited_429');
let diagnosticFailures = 0;

export const options = {
  scenarios: { operations: { executor: 'constant-arrival-rate', rate: OPERATIONS_RATE, timeUnit: '1s', duration: __ENV.DURATION || '2m', preAllocatedVUs: 10, maxVUs: 60 } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<800', 'p(99)<1500'] }
};

export default function () {
  const headers = { Authorization: `Bearer ${BEARER}` };
  const id = encodeURIComponent(EVENT_ID);
  const checks = [
    ['summary', http.get(`${BASE_URL}/api/v1/admin/events/${id}/operations/summary`, { headers, tags: { endpoint: 'operations-summary' } })],
    ['tickets', http.get(`${BASE_URL}/api/v1/admin/events/${id}/tickets/cursor?size=50`, { headers, tags: { endpoint: 'operations-tickets' } })],
    ['orders', http.get(`${BASE_URL}/api/v1/admin/events/${id}/orders/cursor?size=50`, { headers, tags: { endpoint: 'operations-orders' } })],
    ['health', http.get(`${BASE_URL}/api/v1/admin/ops/health`, { headers, tags: { endpoint: 'operations-health' } })],
    ['finance', http.get(`${BASE_URL}/api/v1/finance/overview`, { headers, tags: { endpoint: 'finance-overview' } })]
  ];
  for (const [name, res] of checks) {
    rateLimitedResponses.add(res.status === 429 ? 1 : 0);
    if (res.status !== 200 && diagnosticFailures < 5) {
      diagnosticFailures += 1;
      console.error(`[operations] ${name} unexpected HTTP status=${res.status}; response body and admin token omitted`);
    }
    check(res, { [`${name} is 200`]: r => r.status === 200 });
  }
}
