import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    public_event_reads: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 50),
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: Number(__ENV.VUS || 20),
      maxVUs: Number(__ENV.MAX_VUS || 100),
    },
  },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<500'] },
};

const baseUrl = (__ENV.BASE_URL || '').replace(/\/$/, '');
const slug = __ENV.EVENT_SLUG;
if (!baseUrl || !slug) throw new Error('Set BASE_URL and EVENT_SLUG.');

export default function () {
  const res = http.get(`${baseUrl}/api/v1/public/events/${encodeURIComponent(slug)}`, { tags: { endpoint: 'public-event' } });
  check(res, {
    'event endpoint is HTTP 200': r => r.status === 200,
    'event response is JSON': r => (r.headers['Content-Type'] || '').includes('application/json'),
  });
}
