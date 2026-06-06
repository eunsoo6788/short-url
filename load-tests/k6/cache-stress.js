import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

const managementBaseUrl = __ENV.MANAGEMENT_BASE_URL || 'http://host.docker.internal:8080';
const redirectBaseUrl = __ENV.REDIRECT_BASE_URL || 'http://host.docker.internal:8081';

const spreadCodeCount = Number(__ENV.SPREAD_CODE_COUNT || 200);
const coldBurstVus = Number(__ENV.COLD_BURST_VUS || 100);
const coldBurstDuration = __ENV.COLD_BURST_DURATION || '20s';
const hotRate = Number(__ENV.HOT_RATE || 1000);
const hotDuration = __ENV.HOT_DURATION || '1m';
const hotPreAllocatedVus = Number(__ENV.HOT_PRE_ALLOCATED_VUS || 200);
const hotMaxVus = Number(__ENV.HOT_MAX_VUS || 1000);
const spreadRate = Number(__ENV.SPREAD_RATE || 500);
const spreadDuration = __ENV.SPREAD_DURATION || '1m';
const spreadPreAllocatedVus = Number(__ENV.SPREAD_PRE_ALLOCATED_VUS || 100);
const spreadMaxVus = Number(__ENV.SPREAD_MAX_VUS || 500);
const sleepSeconds = Number(__ENV.SLEEP_SECONDS || 0);

const redirectStatusFailures = new Counter('short_url_redirect_status_failures');

export const options = {
  scenarios: {
    cold_hot_key_burst: {
      executor: 'constant-vus',
      vus: coldBurstVus,
      duration: coldBurstDuration,
      gracefulStop: '0s',
      exec: 'coldHotKeyBurst',
      tags: {
        scenario_type: 'cold_hot_key_burst',
      },
    },
    sustained_hot_key: {
      executor: 'constant-arrival-rate',
      rate: hotRate,
      timeUnit: '1s',
      duration: hotDuration,
      preAllocatedVUs: hotPreAllocatedVus,
      maxVUs: hotMaxVus,
      startTime: coldBurstDuration,
      exec: 'sustainedHotKey',
      tags: {
        scenario_type: 'sustained_hot_key',
      },
    },
    spread_key_stress: {
      executor: 'constant-arrival-rate',
      rate: spreadRate,
      timeUnit: '1s',
      duration: spreadDuration,
      preAllocatedVUs: spreadPreAllocatedVus,
      maxVUs: spreadMaxVus,
      startTime: coldBurstDuration,
      exec: 'spreadKeyStress',
      tags: {
        scenario_type: 'spread_key_stress',
      },
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'checks{endpoint:redirect_short_url}': ['rate>0.99'],
    'http_req_duration{scenario_type:cold_hot_key_burst}': ['p(95)<300', 'p(99)<800'],
    'http_req_duration{scenario_type:sustained_hot_key}': ['p(95)<100', 'p(99)<300'],
    'http_req_duration{scenario_type:spread_key_stress}': ['p(95)<150', 'p(99)<500'],
    short_url_redirect_status_failures: ['count<1'],
  },
};

export function setup() {
  const runId = `${Date.now()}-${Math.floor(Math.random() * 100000)}`;
  const hotCode = createShortUrl(runId, 'hot-key');
  const spreadCodes = [];

  for (let index = 0; index < spreadCodeCount; index += 1) {
    spreadCodes.push(createShortUrl(runId, `spread-${index}`));
  }

  return {
    hotCode,
    spreadCodes,
    runId,
  };
}

export function coldHotKeyBurst(data) {
  redirect(data.hotCode, 'cold_hot_key_burst');
  maybeSleep();
}

export function sustainedHotKey(data) {
  redirect(data.hotCode, 'sustained_hot_key');
  maybeSleep();
}

export function spreadKeyStress(data) {
  const index = exec.scenario.iterationInTest % data.spreadCodes.length;
  redirect(data.spreadCodes[index], 'spread_key_stress');
  maybeSleep();
}

function createShortUrl(runId, suffix) {
  const response = http.post(
    `${managementBaseUrl}/api/v1/short-links`,
    JSON.stringify({
      originalUrl: `https://example.com/k6/cache-stress/${runId}/${suffix}`,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': `k6-cache-stress-setup-${runId}-${suffix}`,
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

function redirect(code, scenarioType) {
  const response = http.get(`${redirectBaseUrl}/${code}`, {
    redirects: 0,
    headers: {
      'X-Request-Id': `k6-cache-stress-${scenarioType}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`,
    },
    tags: {
      name: '/:code',
      endpoint: 'redirect_short_url',
      scenario_type: scenarioType,
    },
  });

  const ok = check(
    response,
    {
      'redirect status is 302': (res) => res.status === 302,
      'redirect location exists': (res) => Boolean(res.headers.Location),
    },
    {
      endpoint: 'redirect_short_url',
      scenario_type: scenarioType,
    },
  );

  if (!ok) {
    redirectStatusFailures.add(1, {
      endpoint: 'redirect_short_url',
      scenario_type: scenarioType,
      status: String(response.status),
    });
  }
}

function maybeSleep() {
  if (sleepSeconds > 0) {
    sleep(sleepSeconds);
  }
}
