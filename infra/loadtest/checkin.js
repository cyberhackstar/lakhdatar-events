import http from 'k6/http';
import { check, fail } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const EVENT_ID = __ENV.EVENT_ID || '';
const GATE = __ENV.GATE || 'Gate 1';
const STAFF_BEARER = __ENV.STAFF_BEARER || '';
const TOKENS = (__ENV.CHECKIN_QR_TOKENS || '').split(',').map(x => x.trim()).filter(Boolean);
const RATE = Number(__ENV.CHECKIN_RATE || 2);
const DURATION = __ENV.CHECKIN_DURATION || '2m';

if (!BASE_URL || !EVENT_ID || !STAFF_BEARER || TOKENS.length === 0) fail('BASE_URL, EVENT_ID, STAFF_BEARER and CHECKIN_QR_TOKENS are required.');
if (!Number.isFinite(RATE) || RATE <= 0) fail('CHECKIN_RATE must be a positive number.');

const contract = new Rate('checkin_contract');
const serverErrors = new Rate('checkin_server_errors');

export const options = {
  scenarios: {
    scanner: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(10, Math.ceil(RATE * 2)),
      maxVUs: Math.max(50, Math.ceil(RATE * 6))
    }
  },
  thresholds: {
    checkin_contract: ['rate>0.98'],
    checkin_server_errors: ['rate<0.01'],
    http_req_duration: ['p(95)<350', 'p(99)<700']
  }
};

export default function () {
  const token = TOKENS[(__ITER + __VU) % TOKENS.length];
  const res = http.post(`${BASE_URL}/api/v1/checkin/scan`, JSON.stringify({ eventId: EVENT_ID, gate: GATE, qrToken: token }), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${STAFF_BEARER}` },
    tags: { endpoint: 'checkin' }
  });
  const expected = [200, 409, 429].includes(res.status);
  contract.add(expected);
  serverErrors.add(res.status >= 500);
  check(res, { 'check-in endpoint returns business result': r => expected });
}
