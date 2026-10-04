import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const TICKET_ID = __ENV.TICKET_ID || '';
const TICKET_TOKEN = __ENV.TICKET_TOKEN || '';
if (!BASE_URL || !TICKET_ID || !TICKET_TOKEN) fail('BASE_URL, TICKET_ID and TICKET_TOKEN are required.');

export const options = {
  scenarios: { pdf: { executor: 'constant-arrival-rate', rate: Number(__ENV.RATE || 5), timeUnit: '1s', duration: __ENV.DURATION || '1m', preAllocatedVUs: 10, maxVUs: 50 } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<1000', 'p(99)<2000'] }
};

export default function () {
  const r = http.get(`${BASE_URL}/api/v1/public/tickets/${encodeURIComponent(TICKET_ID)}/pdf`, { headers: { 'X-Ticket-Token': TICKET_TOKEN }, tags: { endpoint: 'ticket-pdf' } });
  check(r, { 'pdf download is 200': x => x.status === 200, 'pdf content type': x => (x.headers['Content-Type'] || '').toLowerCase().includes('application/pdf') });
}
