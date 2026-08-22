import http from 'k6/http';
import { check, sleep } from 'k6';

// Define the load test phases / scaling behavior
export const options = {
  stages: [
    { duration: '30s', target: 20 }, // Ramp-up to 20 virtual users (VUs)
    { duration: '1m', target: 50 },  // Scale up further to 50 VUs
    { duration: '30s', target: 0 },  // Ramp-down to 0 VUs
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests must complete under 500ms
    http_req_failed: ['rate<0.01'],   // Error rate must be less than 1%
  },
};

// Target execution function (simulating a default test lambda function)
const FUNCTION_NAME = 'hello-world';
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const url = `${BASE_URL}/api/v1/lambda/invoke/${FUNCTION_NAME}`;
  const payload = JSON.stringify({
    event: 'loadtest',
    timestamp: new Date().toISOString(),
    data: {
      message: 'Running scale verification load test'
    }
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-Correlation-ID': `k6-scale-test-${__VU}-${__ITER}`,
    },
  };

  const response = http.post(url, payload, params);

  // Validate the response status is 200 OK or 500 (representing lambda failures, but not gateway crashes)
  check(response, {
    'status is 200': (r) => r.status === 200,
    'has exit code header': (r) => r.headers['X-Lambda-Exit-Code'] !== undefined,
    'has correlation ID header': (r) => r.headers['X-Correlation-ID'] !== undefined,
  });

  sleep(1); // Wait 1 second between iterations per virtual user
}
