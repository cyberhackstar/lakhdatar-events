import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const EVENT_ID = __ENV.ADMIN_EVENT_ID || '';
const BEARER = __ENV.ADMIN_BEARER || '';
if (!BASE_URL || !EVENT_ID || !BEARER) fail('BASE_URL, ADMIN_EVENT_ID and ADMIN_BEARER are required.');

export const options = {
  scenarios: { operations: { executor: 'constant-arrival-rate', rate: Number(__ENV.RATE || 10), timeUnit: '1s', duration: __ENV.DURATION || '2m', preAllocatedVUs: 10, maxVUs: 60 } },
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
  for (const [name, res] of checks) check(res, { [`${name} is 200`]: r => r.status === 200 });
}
