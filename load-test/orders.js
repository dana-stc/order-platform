// k6 run load-test/orders.js            (BASE_URL defaults to the gateway on localhost:8080)
// Mix: 85% valid orders, 10% payment declined (-> compensation), 5% unknown SKU (-> rejected at step 1).
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
// export TOKEN=$(scripts/token.sh load-test 7200)
const TOKEN = __ENV.TOKEN;
const sagaTime = new Trend('saga_completion_ms', true);

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-arrival-rate',
      startRate: 5, timeUnit: '1s', preAllocatedVUs: 50, maxVUs: 200,
      stages: [
        { target: 50, duration: '1m' },
        { target: 50, duration: '3m' },   // steady state
        { target: 0, duration: '30s' },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:create}': ['p(95)<300'],
    saga_completion_ms: ['p(95)<3000'],   // end-to-end async processing, sampled below
  },
};

function body() {
  const r = Math.random();
  if (r < 0.85) return { customerId: `c-${__VU}`, items: [{ sku: 'SKU-MOUSE', quantity: 1, unitPrice: 20.0 }] };
  if (r < 0.95) return { customerId: `c-${__VU}`, items: [{ sku: 'SKU-LAPTOP', quantity: 2, unitPrice: 600.0 }] };
  return { customerId: `c-${__VU}`, items: [{ sku: 'SKU-DOES-NOT-EXIST', quantity: 1, unitPrice: 1.0 }] };
}

export default function () {
  const key = `${__VU}-${__ITER}-${Date.now()}`;
  const res = http.post(`${BASE_URL}/api/orders`, JSON.stringify(body()), {
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key, Authorization: `Bearer ${TOKEN}` },
    tags: { name: 'create' },
  });
  check(res, { 'accepted (202)': (r) => r.status === 202 });

  // Sample ~5% of orders: poll until the saga reaches a final state.
  if (res.status === 202 && Math.random() < 0.05) {
    const id = res.json('id');
    const start = Date.now();
    for (let i = 0; i < 40; i++) {
      const s = http.get(`${BASE_URL}/api/orders/${id}`, { headers: { Authorization: `Bearer ${TOKEN}` }, tags: { name: 'get' } }).json('status');
      if (s === 'CONFIRMED' || s === 'REJECTED') { sagaTime.add(Date.now() - start); break; }
      sleep(0.25);
    }
  }
}
