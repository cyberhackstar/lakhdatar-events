import http from 'k6/http';
import { check, group } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:4002').replace(/\/$/, '');
const EVENT_SLUG = __ENV.EVENT_SLUG || '';
const TICKET_ID = __ENV.TICKET_ID || '';
const TICKET_TOKEN = __ENV.TICKET_TOKEN || '';
const ADMIN_BEARER = __ENV.ADMIN_BEARER || '';
const ADMIN_EVENT_ID = __ENV.ADMIN_EVENT_ID || '';

export const options = {
  scenarios: {
    public_browse: {
      executor: 'ramping-arrival-rate', startRate: 5, timeUnit: '1s', preAllocatedVUs: 20, maxVUs: 150,
      stages: [
        { target: 10, duration: '1m' },
        { target: 25, duration: '2m' },
        { target: 50, duration: '2m' },
        { target: 25, duration: '1m' },
        { target: 5, duration: '1m' }
      ], gracefulStop: '30s'
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    public_errors: ['count<25']
  }
};

const publicErrors = new Counter('public_errors');

function get(path, tags={}) {
  const res = http.get(`${BASE_URL}${path}`, { tags });
  check(res, { 'HTTP 2xx/3xx': r => r.status >= 200 && r.status < 400 });
  if (res.status >= 400) publicErrors.add(1);
  return res;
}

export default function () {
  group('catalog', () => {
    get('/api/v1/public/events/upcoming', { endpoint: 'upcoming' });
    get('/api/v1/public/events/featured', { endpoint: 'featured' });
    get('/api/v1/public/events/facets', { endpoint: 'facets' });
    if (EVENT_SLUG) get(`/api/v1/public/events/${encodeURIComponent(EVENT_SLUG)}`, { endpoint: 'event-detail' });
  });

  if (TICKET_ID && TICKET_TOKEN) {
    group('ticket-access', () => {
      const r = http.get(`${BASE_URL}/api/v1/public/tickets/${encodeURIComponent(TICKET_ID)}`, { headers: { 'X-Ticket-Token': TICKET_TOKEN }, tags: { endpoint: 'ticket' } });
      check(r, { 'ticket access ok': x => x.status === 200 });
    });
  }

  if (ADMIN_BEARER && ADMIN_EVENT_ID) {
    group('admin-read', () => {
      const headers = { Authorization: `Bearer ${ADMIN_BEARER}` };
      const r1 = http.get(`${BASE_URL}/api/v1/admin/events/${encodeURIComponent(ADMIN_EVENT_ID)}`, { headers, tags: { endpoint: 'admin-event' } });
      const r2 = http.get(`${BASE_URL}/api/v1/admin/events/${encodeURIComponent(ADMIN_EVENT_ID)}/tickets/cursor?size=50`, { headers, tags: { endpoint: 'admin-tickets-cursor' } });
      check(r1, { 'admin event ok': x => x.status === 200 });
      check(r2, { 'admin tickets cursor ok': x => x.status === 200 });
    });
  }
}
