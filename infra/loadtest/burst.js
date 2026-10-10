import http from 'k6/http';
import { check } from 'k6';

const baseUrl = (__ENV.BASE_URL || '').replace(/\/$/, '');
const slug = __ENV.EVENT_SLUG || '';
if (!baseUrl) throw new Error('Set BASE_URL.');
const targetPath = slug
  ? `/api/v1/public/events/${encodeURIComponent(slug)}`
  : '/api/v1/public/events/featured';

export const options = {
  scenarios: {
    burst_reads: {
      executor: 'ramping-arrival-rate',
      startRate: Number(__ENV.BURST_START_RATE || 50), timeUnit: '1s',
      preAllocatedVUs: Number(__ENV.BURST_PREALLOCATED_VUS || 100),
      maxVUs: Number(__ENV.BURST_MAX_VUS || 500),
      stages: [
        { target: Number(__ENV.BURST_RATE_1 || 100), duration: '30s' },
        { target: Number(__ENV.BURST_RATE_2 || 200), duration: '60s' },
        { target: Number(__ENV.BURST_RATE_3 || 250), duration: '60s' },
        { target: Number(__ENV.BURST_RATE_2 || 200), duration: '30s' },
        { target: Number(__ENV.BURST_RATE_1 || 100), duration: '30s' }
      ],
      gracefulStop: '30s'
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<600', 'p(99)<1200']
  }
};

export default function () {
  const r = http.get(`${baseUrl}${targetPath}`, { tags: { endpoint: slug ? 'public-event-burst' : 'featured-event-burst' } });
  check(r, { 'event remains HTTP 200': x => x.status === 200, 'event response remains JSON': x => (x.headers['Content-Type'] || '').includes('application/json') });
}
