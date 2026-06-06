import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 400, 409, 413));

const managementBaseUrl = __ENV.MANAGEMENT_BASE_URL || 'http://host.docker.internal:8080';
const redirectBaseUrl = __ENV.REDIRECT_BASE_URL || 'http://host.docker.internal:8081';

const longQueryVus = Number(__ENV.PATHOLOGICAL_LONG_QUERY_VUS || 50);
const longQueryDuration = __ENV.PATHOLOGICAL_LONG_QUERY_DURATION || '30s';
const longQueryBytes = Number(__ENV.PATHOLOGICAL_LONG_QUERY_BYTES || 2000);
const invalidCreateRate = Number(__ENV.PATHOLOGICAL_INVALID_CREATE_RATE || 50);
const invalidCreateDuration = __ENV.PATHOLOGICAL_INVALID_CREATE_DURATION || '30s';
const duplicateCreateRate = Number(__ENV.PATHOLOGICAL_DUPLICATE_CREATE_RATE || 20);
const duplicateCreateDuration = __ENV.PATHOLOGICAL_DUPLICATE_CREATE_DURATION || '30s';
const sleepSeconds = Number(__ENV.SLEEP_SECONDS || 0);

const statusFailures = new Counter('short_url_pathological_status_failures');

const scenarios = {};
if (longQueryVus > 0) {
  scenarios.long_query_redirect = {
    executor: 'constant-vus',
    vus: longQueryVus,
    duration: longQueryDuration,
    exec: 'longQueryRedirect',
    tags: {
      scenario_type: 'long_query_redirect',
    },
  };
}
if (invalidCreateRate > 0) {
  scenarios.invalid_create_payloads = {
    executor: 'constant-arrival-rate',
    rate: invalidCreateRate,
    timeUnit: '1s',
    duration: invalidCreateDuration,
    preAllocatedVUs: 50,
    maxVUs: 300,
    exec: 'invalidCreatePayload',
    tags: {
      scenario_type: 'invalid_create_payloads',
    },
  };
}
if (duplicateCreateRate > 0) {
  scenarios.duplicate_custom_code_race = {
    executor: 'constant-arrival-rate',
    rate: duplicateCreateRate,
    timeUnit: '1s',
    duration: duplicateCreateDuration,
    preAllocatedVUs: 20,
    maxVUs: 100,
    exec: 'duplicateCustomCodeRace',
    tags: {
      scenario_type: 'duplicate_custom_code_race',
    },
  };
}

const thresholds = {
  http_req_failed: ['rate<0.05'],
  short_url_pathological_status_failures: ['count<1'],
};
if (longQueryVus > 0) {
  thresholds['checks{scenario_type:long_query_redirect}'] = ['rate>0.99'];
  thresholds['http_req_duration{scenario_type:long_query_redirect}'] = ['p(95)<300', 'p(99)<800'];
}
if (invalidCreateRate > 0) {
  thresholds['checks{scenario_type:invalid_create_payloads}'] = ['rate>0.99'];
  thresholds['http_req_duration{scenario_type:invalid_create_payloads}'] = ['p(95)<500', 'p(99)<1000'];
}
if (duplicateCreateRate > 0) {
  thresholds['checks{scenario_type:duplicate_custom_code_race}'] = ['rate>0.99'];
  thresholds['http_req_duration{scenario_type:duplicate_custom_code_race}'] = ['p(95)<500', 'p(99)<1000'];
}

export const options = {
  scenarios,
  thresholds,
};

export function setup() {
  const runId = `${Date.now().toString(36)}${Math.floor(Math.random() * 100000).toString(36)}`;
  const code = createShortUrl(runId);
  const duplicateCode = shortCode(`dupe${runId}`);

  const firstDuplicate = createShortUrl(runId, duplicateCode);
  if (firstDuplicate !== duplicateCode) {
    fail(`Expected duplicate setup code=${duplicateCode}, got=${firstDuplicate}`);
  }

  return {
    code,
    duplicateCode,
    longQuery: `q=${'x'.repeat(longQueryBytes)}`,
    longTraceId: `trace-${'y'.repeat(300)}`,
    oversizedUrl: `https://example.com/${'z'.repeat(4096)}`,
  };
}

export function longQueryRedirect(data) {
  const response = http.get(`${redirectBaseUrl}/${data.code}?${data.longQuery}`, {
    redirects: 0,
    headers: {
      'X-Request-Id': data.longTraceId,
    },
    tags: {
      endpoint: 'pathological_long_query_redirect',
      scenario_type: 'long_query_redirect',
    },
  });

  recordCheck(
    response,
    'long query redirect is still 302',
    (res) => res.status === 302 && Boolean(res.headers.Location),
    'long_query_redirect',
  );
  maybeSleep();
}

export function invalidCreatePayload(data) {
  const response = http.post(
    `${managementBaseUrl}/api/v1/short-links`,
    JSON.stringify({
      originalUrl: data.oversizedUrl,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': `k6-pathological-invalid-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`,
      },
      tags: {
        endpoint: 'pathological_invalid_create',
        scenario_type: 'invalid_create_payloads',
      },
    },
  );

  recordCheck(
    response,
    'oversized create is rejected fast',
    (res) => res.status === 400 || res.status === 413,
    'invalid_create_payloads',
  );
  maybeSleep();
}

export function duplicateCustomCodeRace(data) {
  const response = http.post(
    `${managementBaseUrl}/api/v1/short-links`,
    JSON.stringify({
      originalUrl: `https://example.com/pathological/duplicate/${exec.scenario.iterationInTest}`,
      customCode: data.duplicateCode,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': `k6-pathological-dupe-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`,
      },
      tags: {
        endpoint: 'pathological_duplicate_create',
        scenario_type: 'duplicate_custom_code_race',
      },
    },
  );

  recordCheck(
    response,
    'duplicate custom code is rejected as 409',
    (res) => res.status === 409,
    'duplicate_custom_code_race',
  );
  maybeSleep();
}

function createShortUrl(runId, customCode = null) {
  const body = {
    originalUrl: `https://example.com/pathological/${runId}/${customCode || 'warm'}`,
  };

  if (customCode !== null) {
    body.customCode = customCode;
  }

  const response = http.post(
    `${managementBaseUrl}/api/v1/short-links`,
    JSON.stringify(body),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Request-Id': `k6-pathological-setup-${runId}-${customCode || 'warm'}`,
      },
      tags: {
        endpoint: 'pathological_setup_create',
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

function recordCheck(response, name, predicate, scenarioType) {
  const ok = check(
    response,
    {
      [name]: predicate,
    },
    {
      scenario_type: scenarioType,
    },
  );

  if (!ok) {
    statusFailures.add(1, {
      scenario_type: scenarioType,
      status: String(response.status),
    });
  }
}

function shortCode(value) {
  return value.replace(/[^A-Za-z0-9_-]/g, '').slice(0, 32).padEnd(4, '0');
}

function maybeSleep() {
  if (sleepSeconds > 0) {
    sleep(sleepSeconds);
  }
}
