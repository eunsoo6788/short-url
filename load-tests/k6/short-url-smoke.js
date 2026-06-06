import http from 'k6/http';
import { check, fail } from 'k6';

const managementBaseUrl = __ENV.MANAGEMENT_BASE_URL || 'http://host.docker.internal:8080';
const redirectBaseUrl = __ENV.REDIRECT_BASE_URL || 'http://host.docker.internal:8081';

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  const createResponse = http.post(
    `${managementBaseUrl}/api/v1/short-links`,
    JSON.stringify({
      originalUrl: `https://example.com/k6-smoke?run=${Date.now()}`,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': `k6-smoke-${Date.now()}`,
      },
      tags: {
        name: '/api/v1/short-links',
        endpoint: 'create_short_url',
      },
    },
  );

  const created = check(createResponse, {
    'create status is 201': (res) => res.status === 201,
    'create response has code': (res) => Boolean(res.json('code')),
  });

  if (!created) {
    fail(`Failed to create short URL: status=${createResponse.status} body=${createResponse.body}`);
  }

  const code = createResponse.json('code');
  const redirectResponse = http.get(`${redirectBaseUrl}/${code}`, {
    redirects: 0,
    headers: {
      'X-Request-Id': `k6-smoke-redirect-${code}`,
    },
    tags: {
      name: '/:code',
      endpoint: 'redirect_short_url',
    },
  });

  check(redirectResponse, {
    'redirect status is 302': (res) => res.status === 302,
    'redirect location exists': (res) => Boolean(res.headers.Location),
  });
}
