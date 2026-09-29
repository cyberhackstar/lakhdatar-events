import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const accepted = new Counter('accepted_checkins');

export const options = {
  scenarios: {
    concurrent_scans: {
      executor: 'per-vu-iterations',
      vus: Number(__ENV.VUS || 50),
      iterations: 2,
      maxDuration: '60s',
    },
  },
  thresholds: {
    accepted_checkins: ['count<=1'],
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<500'],
  },
};

const baseUrl = (__ENV.BASE_URL || '').replace(/\/$/, '');
const eventId = __ENV.EVENT_ID;
const gate = __ENV.GATE || 'Main Gate';
const token = __ENV.STAFF_TOKEN;
const qrToken = __ENV.QR_TOKEN;

if (!baseUrl || !eventId || !token || !qrToken) {
  throw new Error('Set BASE_URL, EVENT_ID, STAFF_TOKEN, and a fresh unused QR_TOKEN.');
}

export default function () {
  const res = http.post(`${baseUrl}/api/v1/checkin/scan`, JSON.stringify({ eventId, gate, qrToken }), {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    tags: { endpoint: 'checkin-scan' },
  });

  let body = null;
  try { body = JSON.parse(res.body); } catch (_) {}
  if (body?.result === 'ACCEPTED') accepted.add(1);

  check(res, {
    'check-in response is HTTP 200': r => r.status === 200,
    'response has a result': r => !!body?.result,
  });
}
