import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
if (!BASE_URL) throw new Error('BASE_URL is required.');

export const options = {
  scenarios: { seo: { executor: 'constant-arrival-rate', rate: Number(__ENV.RATE || 5), timeUnit: '1s', duration: __ENV.DURATION || '1m', preAllocatedVUs: 10, maxVUs: 40 } },
  thresholds: { http_req_failed: ['rate<0.01'], http_req_duration: ['p(95)<500', 'p(99)<1000'] }
};

export default function () {
  const paths = ['/robots.txt', '/sitemap.xml', '/sitemap-1.xml'];
  for (const path of paths) {
    const r = http.get(`${BASE_URL}${path}`, { tags: { endpoint: path.slice(1).replace(/[^a-z0-9]+/gi, '-') } });
    check(r, { [`${path} available`]: x => x.status === 200 });
  }
}
