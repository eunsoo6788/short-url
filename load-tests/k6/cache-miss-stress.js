import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 404));

const redirectBaseUrl = __ENV.REDIRECT_BASE_URL || 'http://host.docker.internal:8081';

const missingCodeCount = Number(__ENV.MISSING_CODE_COUNT || 200);
const coldBurstVus = Number(__ENV.MISS_COLD_BURST_VUS || 100);
const coldBurstDuration = __ENV.MISS_COLD_BURST_DURATION || '20s';
const hotRate = Number(__ENV.MISS_HOT_RATE || 1000);
const hotDuration = __ENV.MISS_HOT_DURATION || '1m';
const hotPreAllocatedVus = Number(__ENV.MISS_HOT_PRE_ALLOCATED_VUS || 200);
const hotMaxVus = Number(__ENV.MISS_HOT_MAX_VUS || 1000);
const spreadRate = Number(__ENV.MISS_SPREAD_RATE || 500);
const spreadDuration = __ENV.MISS_SPREAD_DURATION || '1m';
const spreadPreAllocatedVus = Number(__ENV.MISS_SPREAD_PRE_ALLOCATED_VUS || 100);
const spreadMaxVus = Number(__ENV.MISS_SPREAD_MAX_VUS || 500);
const uniqueScanRate = Number(__ENV.MISS_UNIQUE_SCAN_RATE || 200);
const uniqueScanDuration = __ENV.MISS_UNIQUE_SCAN_DURATION || '1m';
const uniqueScanPreAllocatedVus = Number(__ENV.MISS_UNIQUE_SCAN_PRE_ALLOCATED_VUS || 100);
const uniqueScanMaxVus = Number(__ENV.MISS_UNIQUE_SCAN_MAX_VUS || 500);
const sleepSeconds = Number(__ENV.SLEEP_SECONDS || 0);

const missingStatusFailures = new Counter('short_url_missing_status_failures');

export const options = {
  scenarios: {
    cold_missing_hot_key_burst: {
      executor: 'constant-vus',
      vus: coldBurstVus,
      duration: coldBurstDuration,
      gracefulStop: '0s',
      exec: 'coldMissingHotKeyBurst',
      tags: {
        scenario_type: 'cold_missing_hot_key_burst',
      },
    },
    sustained_missing_hot_key: {
      executor: 'constant-arrival-rate',
      rate: hotRate,
      timeUnit: '1s',
      duration: hotDuration,
      preAllocatedVUs: hotPreAllocatedVus,
      maxVUs: hotMaxVus,
      startTime: coldBurstDuration,
      exec: 'sustainedMissingHotKey',
      tags: {
        scenario_type: 'sustained_missing_hot_key',
      },
    },
    spread_missing_key_stress: {
      executor: 'constant-arrival-rate',
      rate: spreadRate,
      timeUnit: '1s',
      duration: spreadDuration,
      preAllocatedVUs: spreadPreAllocatedVus,
      maxVUs: spreadMaxVus,
      startTime: coldBurstDuration,
      exec: 'spreadMissingKeyStress',
      tags: {
        scenario_type: 'spread_missing_key_stress',
      },
    },
    unique_missing_key_scan: {
      executor: 'constant-arrival-rate',
      rate: uniqueScanRate,
      timeUnit: '1s',
      duration: uniqueScanDuration,
      preAllocatedVUs: uniqueScanPreAllocatedVus,
      maxVUs: uniqueScanMaxVus,
      startTime: coldBurstDuration,
      exec: 'uniqueMissingKeyScan',
      tags: {
        scenario_type: 'unique_missing_key_scan',
      },
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'checks{endpoint:redirect_missing_short_url}': ['rate>0.99'],
    'http_req_duration{scenario_type:cold_missing_hot_key_burst}': ['p(95)<300', 'p(99)<800'],
    'http_req_duration{scenario_type:sustained_missing_hot_key}': ['p(95)<100', 'p(99)<300'],
    'http_req_duration{scenario_type:spread_missing_key_stress}': ['p(95)<150', 'p(99)<500'],
    'http_req_duration{scenario_type:unique_missing_key_scan}': ['p(95)<500', 'p(99)<1000'],
    short_url_missing_status_failures: ['count<1'],
  },
};

export function setup() {
  const runId = cleanCodePart(`${Date.now().toString(36)}${Math.floor(Math.random() * 100000).toString(36)}`);
  const hotMissingCode = shortCode(`nf${runId}hot`);
  const spreadMissingCodes = [];

  for (let index = 0; index < missingCodeCount; index += 1) {
    spreadMissingCodes.push(shortCode(`nf${runId}${index.toString(36)}`));
  }

  return {
    runId,
    hotMissingCode,
    spreadMissingCodes,
  };
}

export function coldMissingHotKeyBurst(data) {
  redirectMissing(data.hotMissingCode, 'cold_missing_hot_key_burst');
  maybeSleep();
}

export function sustainedMissingHotKey(data) {
  redirectMissing(data.hotMissingCode, 'sustained_missing_hot_key');
  maybeSleep();
}

export function spreadMissingKeyStress(data) {
  const index = exec.scenario.iterationInTest % data.spreadMissingCodes.length;
  redirectMissing(data.spreadMissingCodes[index], 'spread_missing_key_stress');
  maybeSleep();
}

export function uniqueMissingKeyScan(data) {
  const code = shortCode(`nq${data.runId}${exec.scenario.iterationInTest.toString(36)}${exec.vu.idInTest.toString(36)}`);
  redirectMissing(code, 'unique_missing_key_scan');
  maybeSleep();
}

function redirectMissing(code, scenarioType) {
  const response = http.get(`${redirectBaseUrl}/${code}`, {
    redirects: 0,
    headers: {
      'X-Request-Id': `k6-cache-miss-${scenarioType}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}`,
    },
    tags: {
      name: '/:missingCode',
      endpoint: 'redirect_missing_short_url',
      scenario_type: scenarioType,
    },
  });

  const ok = check(
    response,
    {
      'missing redirect status is 404': (res) => res.status === 404,
    },
    {
      endpoint: 'redirect_missing_short_url',
      scenario_type: scenarioType,
    },
  );

  if (!ok) {
    missingStatusFailures.add(1, {
      endpoint: 'redirect_missing_short_url',
      scenario_type: scenarioType,
      status: String(response.status),
    });
  }
}

function shortCode(value) {
  const cleaned = cleanCodePart(value);
  const bounded = cleaned.slice(0, 32);

  if (bounded.length >= 4) {
    return bounded;
  }

  return `${bounded}0000`.slice(0, 4);
}

function cleanCodePart(value) {
  return value.replace(/[^A-Za-z0-9_-]/g, '');
}

function maybeSleep() {
  if (sleepSeconds > 0) {
    sleep(sleepSeconds);
  }
}
