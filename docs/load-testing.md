# Load Testing

이 문서는 Short URL 서비스를 k6로 부하 테스트하는 방법을 정리한다.

## 실행 전 준비

로컬 인프라를 먼저 실행한다.

```bash
docker compose up -d postgres valkey prometheus loki promtail grafana
```

앱 서버도 기본 포트로 실행한다.

```bash
./gradlew :apps:management-server:bootRun
./gradlew :apps:redirect-server:bootRun
./gradlew :apps:worker-server:bootRun
```

k6는 Docker container에서 실행되므로 기본 target은 호스트 앱을 가리키는 `host.docker.internal`이다.

```text
MANAGEMENT_BASE_URL=http://host.docker.internal:8080
REDIRECT_BASE_URL=http://host.docker.internal:8081
```

## 스크립트

| Script | 목적 |
| --- | --- |
| `load-tests/k6/short-url-smoke.js` | Short URL 생성 1회와 redirect 1회를 검증 |
| `load-tests/k6/redirect-load.js` | 사전 생성한 short code를 대상으로 redirect read path 부하 테스트 |
| `load-tests/k6/mixed-load.js` | 생성 API와 redirect API를 섞은 read-heavy 부하 테스트 |
| `load-tests/k6/cache-stress.js` | cold hot key, sustained hot key, spread key로 cache stampede/hot key 방어 스트레스 테스트 |
| `load-tests/k6/cache-miss-stress.js` | 존재하지 않는 code의 negative cache, hot missing key, 랜덤 scan 스트레스 테스트 |
| `load-tests/k6/pathological-inputs.js` | 긴 query/header, oversized create payload, duplicate custom code로 입력/로그/DB 제약 경로 스트레스 테스트 |

## Smoke Test

부하 테스트 전에 smoke test로 앱과 k6 연결을 먼저 확인한다.

```bash
docker compose run --rm k6 run /scripts/short-url-smoke.js
```

## Redirect Load Test

기본값:

- `VUS=20`
- `DURATION=1m`
- `SHORT_URL_COUNT=100`

```bash
docker compose run --rm k6 run /scripts/redirect-load.js
```

강도를 조정할 때:

```bash
docker compose run --rm \
  -e VUS=100 \
  -e DURATION=5m \
  -e SHORT_URL_COUNT=1000 \
  k6 run /scripts/redirect-load.js
```

이 테스트는 `setup()` 단계에서 Management Server로 short URL을 생성한 뒤, 테스트 본문에서는 Redirect Server만 반복 호출한다. redirect 요청은 외부 원본 URL까지 따라가지 않도록 `redirects: 0`으로 실행한다.

## Mixed Load Test

기본값:

- `VUS=20`
- `DURATION=1m`
- `SHORT_URL_COUNT=100`
- `CREATE_RATIO=0.05`

```bash
docker compose run --rm k6 run /scripts/mixed-load.js
```

생성 비율을 높일 때:

```bash
docker compose run --rm \
  -e VUS=50 \
  -e DURATION=3m \
  -e SHORT_URL_COUNT=500 \
  -e CREATE_RATIO=0.10 \
  k6 run /scripts/mixed-load.js
```

## Cache Stress Test

캐시 방어 전략을 검증하기 위한 스트레스 테스트다.

- `cold_hot_key_burst`: 한 번도 redirect 하지 않은 hot code에 VU를 순간적으로 몰아 cold cache stampede를 만든다.
- `sustained_hot_key`: 같은 hot code를 높은 arrival rate로 계속 호출해 local cache가 Redis/DB 부하를 흡수하는지 본다.
- `spread_key_stress`: 여러 code를 분산 호출해 일반 redirect read path의 p95/p99와 오류율을 같이 본다.

로컬 기본값은 보수적으로 잡혀 있다.

```bash
docker compose run --rm k6 run /scripts/cache-stress.js
```

강도를 높일 때:

```bash
docker compose run --rm \
  -e COLD_BURST_VUS=300 \
  -e COLD_BURST_DURATION=20s \
  -e HOT_RATE=3000 \
  -e HOT_DURATION=2m \
  -e HOT_PRE_ALLOCATED_VUS=500 \
  -e HOT_MAX_VUS=2000 \
  -e SPREAD_CODE_COUNT=1000 \
  -e SPREAD_RATE=1000 \
  -e SPREAD_DURATION=2m \
  k6 run /scripts/cache-stress.js
```

## Cache Miss Stress Test

없는 code가 반복 호출될 때 PostgreSQL까지 계속 내려가지 않도록 negative cache가 동작하는지 검증한다.

- `cold_missing_hot_key_burst`: 한 번도 조회하지 않은 같은 missing code에 VU를 순간적으로 몰아 negative cache stampede를 만든다.
- `sustained_missing_hot_key`: 같은 missing code를 높은 arrival rate로 계속 호출해 404 negative cache가 Redis/DB 부하를 흡수하는지 본다.
- `spread_missing_key_stress`: 여러 missing code를 반복 호출해 missing set이 cache에 올라간 뒤 p95/p99가 안정되는지 본다.
- `unique_missing_key_scan`: 매 요청마다 새로운 missing code를 호출한다. negative cache로 줄일 수 없는 랜덤 scan의 DB pressure 기준선을 확인한다.

```bash
docker compose run --rm k6 run /scripts/cache-miss-stress.js
```

강도를 높일 때:

```bash
docker compose run --rm \
  -e MISS_COLD_BURST_VUS=300 \
  -e MISS_COLD_BURST_DURATION=20s \
  -e MISS_HOT_RATE=3000 \
  -e MISS_HOT_DURATION=2m \
  -e MISSING_CODE_COUNT=1000 \
  -e MISS_SPREAD_RATE=1000 \
  -e MISS_SPREAD_DURATION=2m \
  -e MISS_UNIQUE_SCAN_RATE=500 \
  -e MISS_UNIQUE_SCAN_DURATION=2m \
  k6 run /scripts/cache-miss-stress.js
```

## Pathological Inputs Test

단순히 RPS만 올리지 않고 정상/비정상 입력의 모양으로 병목을 찾는다.

- `long_query_redirect`: 같은 short code를 redirect하되 긴 query string과 긴 trace header를 붙인다. 캐시는 hit이어도 access log 직렬화/파일 쓰기 경로가 커지는지 본다.
- `invalid_create_payloads`: 2048자를 넘는 URL 생성 요청을 반복해 validation/Jackson parsing 전에 oversized body guard가 400 또는 413으로 빠르게 거절하는지 본다.
- `duplicate_custom_code_race`: 이미 존재하는 custom code 생성을 반복해 DB unique constraint와 예외 변환 경로가 409로 빠르게 끝나는지 본다.

```bash
docker compose run --rm k6 run /scripts/pathological-inputs.js
```

강도를 높일 때:

```bash
docker compose run --rm \
  -e PATHOLOGICAL_LONG_QUERY_VUS=100 \
  -e PATHOLOGICAL_LONG_QUERY_BYTES=3000 \
  -e PATHOLOGICAL_INVALID_CREATE_RATE=100 \
  -e PATHOLOGICAL_DUPLICATE_CREATE_RATE=50 \
  k6 run /scripts/pathological-inputs.js
```

## 환경변수

| Name | Default | 설명 |
| --- | --- | --- |
| `MANAGEMENT_BASE_URL` | `http://host.docker.internal:8080` | Management Server base URL |
| `REDIRECT_BASE_URL` | `http://host.docker.internal:8081` | Redirect Server base URL |
| `VUS` | `20` | 가상 사용자 수 |
| `DURATION` | `1m` | 테스트 시간 |
| `SHORT_URL_COUNT` | `100` | setup 단계에서 만들 short URL 수 |
| `CREATE_RATIO` | `0.05` | mixed-load에서 생성 요청 비율 |
| `SLEEP_SECONDS` | `0` | VU iteration 사이 sleep |
| `SPREAD_CODE_COUNT` | `200` | cache-stress에서 분산 호출할 short URL 수 |
| `COLD_BURST_VUS` | `100` | cache-stress cold hot key burst VU 수 |
| `COLD_BURST_DURATION` | `20s` | cache-stress cold hot key burst 지속 시간 |
| `HOT_RATE` | `1000` | cache-stress sustained hot key 초당 요청 수 |
| `HOT_DURATION` | `1m` | cache-stress sustained hot key 지속 시간 |
| `HOT_PRE_ALLOCATED_VUS` | `200` | cache-stress hot key pre-allocated VU 수 |
| `HOT_MAX_VUS` | `1000` | cache-stress hot key max VU 수 |
| `SPREAD_RATE` | `500` | cache-stress spread key 초당 요청 수 |
| `SPREAD_DURATION` | `1m` | cache-stress spread key 지속 시간 |
| `SPREAD_PRE_ALLOCATED_VUS` | `100` | cache-stress spread key pre-allocated VU 수 |
| `SPREAD_MAX_VUS` | `500` | cache-stress spread key max VU 수 |
| `MISSING_CODE_COUNT` | `200` | cache-miss-stress에서 반복 호출할 missing code 수 |
| `MISS_COLD_BURST_VUS` | `100` | cache-miss-stress cold missing hot key burst VU 수 |
| `MISS_COLD_BURST_DURATION` | `20s` | cache-miss-stress cold missing hot key burst 지속 시간 |
| `MISS_HOT_RATE` | `1000` | cache-miss-stress sustained missing hot key 초당 요청 수 |
| `MISS_HOT_DURATION` | `1m` | cache-miss-stress sustained missing hot key 지속 시간 |
| `MISS_HOT_PRE_ALLOCATED_VUS` | `200` | cache-miss-stress hot missing key pre-allocated VU 수 |
| `MISS_HOT_MAX_VUS` | `1000` | cache-miss-stress hot missing key max VU 수 |
| `MISS_SPREAD_RATE` | `500` | cache-miss-stress spread missing key 초당 요청 수 |
| `MISS_SPREAD_DURATION` | `1m` | cache-miss-stress spread missing key 지속 시간 |
| `MISS_SPREAD_PRE_ALLOCATED_VUS` | `100` | cache-miss-stress spread missing key pre-allocated VU 수 |
| `MISS_SPREAD_MAX_VUS` | `500` | cache-miss-stress spread missing key max VU 수 |
| `MISS_UNIQUE_SCAN_RATE` | `200` | cache-miss-stress unique missing key scan 초당 요청 수 |
| `MISS_UNIQUE_SCAN_DURATION` | `1m` | cache-miss-stress unique missing key scan 지속 시간 |
| `MISS_UNIQUE_SCAN_PRE_ALLOCATED_VUS` | `100` | cache-miss-stress unique scan pre-allocated VU 수 |
| `MISS_UNIQUE_SCAN_MAX_VUS` | `500` | cache-miss-stress unique scan max VU 수 |
| `PATHOLOGICAL_LONG_QUERY_VUS` | `50` | pathological-inputs 긴 query redirect VU 수 |
| `PATHOLOGICAL_LONG_QUERY_DURATION` | `30s` | pathological-inputs 긴 query redirect 지속 시간 |
| `PATHOLOGICAL_LONG_QUERY_BYTES` | `2000` | pathological-inputs query value 길이 |
| `PATHOLOGICAL_INVALID_CREATE_RATE` | `50` | pathological-inputs oversized create 초당 요청 수 |
| `PATHOLOGICAL_INVALID_CREATE_DURATION` | `30s` | pathological-inputs oversized create 지속 시간 |
| `PATHOLOGICAL_DUPLICATE_CREATE_RATE` | `20` | pathological-inputs duplicate custom code 초당 요청 수 |
| `PATHOLOGICAL_DUPLICATE_CREATE_DURATION` | `30s` | pathological-inputs duplicate custom code 지속 시간 |

Management Server는 `short-url.management.create.max-request-body-bytes=4096`을 기본값으로 둔다. 정상 create 요청은 `originalUrl` 도메인 검증 한도보다 여유가 있지만, pathological test의 oversized body는 컨트롤러/Jackson 파싱 전에 `413 Payload Too Large`로 잘려야 한다. body가 `short-url.management.create.max-drain-body-bytes=8192` 이하이면 남은 body를 drain한 뒤 keep-alive를 유지하고, 그보다 크거나 길이를 모르는 상태에서 한도를 넘으면 HTTP 연결 재사용을 막기 위해 `Connection: close`로 응답한다.

`PATHOLOGICAL_LONG_QUERY_VUS=0`, `PATHOLOGICAL_INVALID_CREATE_RATE=0`, `PATHOLOGICAL_DUPLICATE_CREATE_RATE=0`처럼 0을 지정하면 해당 scenario를 비활성화한다. 한 병목만 격리해서 볼 때 사용한다.

## 기본 Threshold

Redirect 중심 테스트 기준:

```text
http_req_failed < 1%
redirect p95 < 100ms
redirect p99 < 300ms
redirect checks > 99%
```

Mixed 테스트 기준:

```text
http_req_failed < 1%
create p95 < 500ms
create p99 < 1000ms
redirect p95 < 100ms
redirect p99 < 300ms
checks > 99%
```

초기 기준은 로컬 개발 환경용이다. 운영 목표는 배포 환경의 리소스, replica 수, 네트워크, Redis/PostgreSQL 성능을 기준으로 별도 정의한다.

## Grafana에서 같이 볼 지표

부하 테스트 중 Grafana에서 다음 dashboard를 같이 확인한다.

```text
Short URL Overview: http://localhost:3000/d/short-url-overview/short-url-overview
Short URL API Logs: http://localhost:3000/d/short-url-api-logs/short-url-api-logs
```

특히 다음 지표를 본다.

- Redirect p95/p99 latency
- HTTP 4xx/5xx 비율
- `short_url_redirect_cache_total`
- `short_url_redirect_resolution_total`
- Hikari active/idle connection
- JVM memory/thread
- API access log의 `duration_ms`, `status`, `uri`

## 해석 가이드

- redirect latency가 높고 Hikari active connection이 높으면 cache hit ratio와 Valkey 상태를 먼저 본다.
- `not_found`가 급증하면 랜덤 code scan 또는 잘못된 클라이언트 호출을 의심한다.
- cache miss가 많으면 TTL, cache warming, hot key 정책을 점검한다.
- 생성 API latency가 높으면 PostgreSQL insert, unique constraint 충돌, connection pool을 확인한다.
- k6 error가 302 redirect를 실패로 보는 경우가 없도록 redirect 테스트는 `redirects: 0`을 유지한다.
