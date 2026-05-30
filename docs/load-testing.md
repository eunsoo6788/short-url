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
