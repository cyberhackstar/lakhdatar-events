import http from 'k6/http';
import { check, group } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://127.0.0.1:4002').replace(/\/$/, '');
const EVENT_SLUG = __ENV.EVENT_SLUG || '';

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

  // Keep this scenario strictly public. Ticket/admin authorization and rate-limit behavior are
  // covered by fixture-backed ticket-pdf.js and operations.js at their own scenario rates.
}
