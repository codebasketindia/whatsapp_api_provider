// k6 load test for the partner public API + queue throughput (Phase 12).
// Run against STAGING with a test partner, a test WABA number and enough wallet balance:
//   k6 run -e BASE_URL=https://staging.example.com -e API_KEY=cbk_live_... -e API_SECRET=cbs_... \
//          -e FROM=919876543210 -e TO=919812345678 -e TEMPLATE=hello_world -e LANG=en_US deploy/loadtest/send-messages.k6.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const accepted = new Rate('accepted');
const latency = new Trend('send_latency_ms');

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-arrival-rate',
      startRate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      stages: [
        { target: 20, duration: '1m' },
        { target: 50, duration: '2m' },
        { target: 0, duration: '30s' },
      ],
    },
  },
  thresholds: {
    accepted: ['rate>0.99'],
    http_req_duration: ['p(95)<800'],
  },
};

const headers = {
  'Content-Type': 'application/json',
  'X-API-KEY': __ENV.API_KEY,
  'X-API-SECRET': __ENV.API_SECRET,
};

export default function () {
  const body = JSON.stringify({
    from: __ENV.FROM,
    to: __ENV.TO,
    type: 'template',
    template: { name: __ENV.TEMPLATE || 'hello_world', language: __ENV.LANG || 'en_US' },
    clientReference: `k6-${__VU}-${__ITER}`,
  });
  const res = http.post(`${__ENV.BASE_URL}/api/v1/public/messages/send`, body, { headers });
  latency.add(res.timings.duration);
  accepted.add(res.status === 200 || res.status === 201);
  check(res, { 'queued': (r) => r.status < 300 && r.json('data.status') === 'QUEUED' });
  sleep(0.1);
}
