import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';

const managementBaseUrl = __ENV.MANAGEMENT_BASE_URL || 'http://host.docker.internal:8080';
const redirectBaseUrl = __ENV.REDIRECT_BASE_URL || 'http://host.docker.internal:8081';
const vus = Number(__ENV.VUS || 20);
const duration = __ENV.DURATION || '1m';
const shortUrlCount = Number(__ENV.SHORT_URL_COUNT || 100);
const createRatio = Number(__ENV.CREATE_RATIO || 0.05);
const sleepSeconds = Number(__ENV.SLEEP_SECONDS || 0);

export const options = {
  scenarios: {
    mixed_load: {
      executor: 'constant-vus',
      vus,
      duration,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{endpoint:create_short_url}': ['p(95)<500', 'p(99)<1000'],
    'http_req_duration{endpoint:redirect_short_url}': ['p(95)<100', 'p(99)<300'],
    'checks{endpoint:create_short_url}': ['rate>0.99'],
    'checks{endpoint:redirect_short_url}': ['rate>0.99'],
  },
};

export function setup() {
  const codes = [];
  const runId = `${Date.now()}-${Math.floor(Math.random() * 100000)}`;

  for (let index = 0; index < shortUrlCount; index += 1) {
    codes.push(createShortUrl(runId, `seed-${index}`, 'setup_create_short_url'));
  }

  return { codes, runId };
}

export default function (data) {
  const shouldCreate = Math.random() < createRatio;

  if (shouldCreate) {
    createShortUrl(
      data.runId,
      `vu-${exec.vu.idInTest}-iter-${exec.scenario.iterationInTest}`,
      'create_short_url',
    );
  } else {
    const index = exec.scenario.iterationInTest % data.codes.length;
    redirect(data.codes[index]);
  }

  if (sleepSeconds > 0) {
    sleep(sleepSeconds);
  }
}

function createShortUrl(runId, suffix, endpoint) {
  const response = http.post(
    `${managementBaseUrl}/api/v1/short-links`,
    JSON.stringify({
      originalUrl: `https://example.com/k6/mixed-load/${runId}/${suffix}`,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': `k6-${endpoint}-${runId}-${suffix}`,
      },
      tags: {
        endpoint,
      },
    },
  );

  const created = check(
    response,
    {
      'create status is 201': (res) => res.status === 201,
      'create response has code': (res) => Boolean(res.json('code')),
    },
    {
      endpoint,
    },
  );

  if (!created) {
    fail(`Failed to create short URL: status=${response.status} body=${response.body}`);
  }

  return response.json('code');
}

function redirect(code) {
  const response = http.get(`${redirectBaseUrl}/${code}`, {
    redirects: 0,
    headers: {
      'X-Request-Id': `k6-mixed-redirect-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`,
    },
    tags: {
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
}
