import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const TICKET_ID = __ENV.TICKET_ID || '';
const TICKET_TOKEN = __ENV.TICKET_TOKEN || '';
const PDF_RATE = Number(__ENV.PDF_RATE || 15);
if (!BASE_URL || !TICKET_ID || !TICKET_TOKEN) fail('BASE_URL, TICKET_ID and TICKET_TOKEN are required.');
if (!Number.isInteger(PDF_RATE) || PDF_RATE < 1 || PDF_RATE > 18) {
  fail('PDF_RATE must be an integer from 1 to 18 RPS; the shared edge API limit is 20 RPS per client.');
}

const rateLimitedResponses = new Counter('pdf_rate_limited_429');
let diagnosticFailures = 0;

export const options = {
  scenarios: { pdf: { executor: 'constant-arrival-rate', rate: PDF_RATE, timeUnit: '1s', duration: __ENV.DURATION || '1m', preAllocatedVUs: 10, maxVUs: 50 } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<1000', 'p(99)<2000'] }
};

export default function () {
  const r = http.get(`${BASE_URL}/api/v1/public/tickets/${encodeURIComponent(TICKET_ID)}/pdf`, { headers: { 'X-Ticket-Token': TICKET_TOKEN }, tags: { endpoint: 'ticket-pdf' } });
  rateLimitedResponses.add(r.status === 429 ? 1 : 0);
  if ((r.status !== 200 || !(r.headers['Content-Type'] || '').toLowerCase().includes('application/pdf')) && diagnosticFailures < 3) {
    diagnosticFailures += 1;
    console.error(`[ticket-pdf] unexpected HTTP status=${r.status} contentType=${String(r.headers['Content-Type'] || 'missing').slice(0, 80)}; response body and ticket token omitted`);
  }
  check(r, { 'pdf download is 200': x => x.status === 200, 'pdf content type': x => (x.headers['Content-Type'] || '').toLowerCase().includes('application/pdf') });
}
