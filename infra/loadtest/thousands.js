import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const EVENT_PATH = __ENV.EVENT_PATH || '/api/v1/public/events/featured';
if (!BASE_URL) throw new Error('BASE_URL is required');

const MAX_VUS = Number(__ENV.THOUSANDS_MAX_VUS || 1000);
const RAMP = __ENV.THOUSANDS_RAMP || '2m';
const HOLD = __ENV.THOUSANDS_HOLD || '3m';

export const options = {
  scenarios: {
    thousands: {
      executor: 'ramping-vus',
      startVUs: 25,
      stages: [
        { duration: RAMP, target: Math.floor(MAX_VUS * 0.25) },
        { duration: RAMP, target: Math.floor(MAX_VUS * 0.50) },
        { duration: RAMP, target: Math.floor(MAX_VUS * 0.75) },
        { duration: RAMP, target: MAX_VUS },
        { duration: HOLD, target: MAX_VUS },
        { duration: '1m', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800', 'p(99)<1500'],
  },
};

export default function () {
  const response = http.get(`${BASE_URL}${EVENT_PATH}`, {
    headers: { Accept: 'application/json' },
    tags: { scenario: 'thousands-public-read' },
  });
  check(response, {
    'public event read is successful': r => r.status === 200,
  });
  sleep(1);
}
