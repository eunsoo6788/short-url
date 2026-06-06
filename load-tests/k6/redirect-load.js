import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';

const managementBaseUrl = __ENV.MANAGEMENT_BASE_URL || 'http://host.docker.internal:8080';
const redirectBaseUrl = __ENV.REDIRECT_BASE_URL || 'http://host.docker.internal:8081';
const vus = Number(__ENV.VUS || 20);
const duration = __ENV.DURATION || '1m';
const shortUrlCount = Number(__ENV.SHORT_URL_COUNT || 100);
const sleepSeconds = Number(__ENV.SLEEP_SECONDS || 0);

export const options = {
  scenarios: {
    redirect_load: {
      executor: 'constant-vus',
      vus,
      duration,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:redirect_short_url}': ['p(95)<100', 'p(99)<300'],
    'checks{endpoint:redirect_short_url}': ['rate>0.99'],
  },
};

export function setup() {
  const codes = [];
  const runId = `${Date.now()}-${Math.floor(Math.random() * 100000)}`;

  for (let index = 0; index < shortUrlCount; index += 1) {
    const code = createShortUrl(runId, index);
    codes.push(code);
  }

  return { codes };
}

export default function (data) {
  const index = exec.scenario.iterationInTest % data.codes.length;
  const code = data.codes[index];
  const response = http.get(`${redirectBaseUrl}/${code}`, {
    redirects: 0,
    headers: {
      'X-Request-Id': `k6-redirect-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`,
    },
    tags: {
      name: '/:code',
      endpoint: 'redirect_short_url',
    },
  });

  check(
    response,
    {
      'redirect status is 302': (res) => res.status === 302,
      'redirect location exists': (res) => Boolean(res.headers.Location),
    },
    {
      endpoint: 'redirect_short_url',
    },
  );

  if (sleepSeconds > 0) {
    sleep(sleepSeconds);
  }
}

function createShortUrl(runId, index) {
  const response = http.post(
    `${managementBaseUrl}/api/v1/short-links`,
    JSON.stringify({
      originalUrl: `https://example.com/k6/redirect-load/${runId}/${index}`,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': `k6-setup-${runId}-${index}`,
      },
      tags: {
        name: '/api/v1/short-links',
        endpoint: 'setup_create_short_url',
      },
    },
  );

  const created = check(response, {
    'setup create status is 201': (res) => res.status === 201,
    'setup create response has code': (res) => Boolean(res.json('code')),
  });

  if (!created) {
    fail(`Failed to create setup short URL: status=${response.status} body=${response.body}`);
  }

  return response.json('code');
}
