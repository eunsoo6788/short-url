# Observability

Short URL 서비스의 로컬 성능 모니터링은 Spring Boot Actuator, Micrometer, Prometheus, Loki, Promtail, Grafana로 구성한다.

## 구성 요소

- 각 앱 서버는 `/actuator/prometheus`로 Prometheus scrape endpoint를 노출한다.
- Prometheus는 Docker Compose에서 실행되며 `host.docker.internal`을 통해 로컬 JVM 앱을 scrape한다.
- Management Server와 Redirect Server는 API access log를 JSON line 파일로 기록한다.
- Promtail은 `logs/` 아래 access log 파일을 tailing해서 Loki로 전송한다.
- Grafana는 Prometheus/Loki datasource와 dashboard를 자동 provisioning한다.

## 로컬 실행

```bash
docker compose up -d prometheus loki promtail grafana
```

접속 정보:

```text
Prometheus: http://localhost:9090
Loki: http://localhost:3100
Grafana: http://localhost:3000
Grafana account: admin / admin
```

Prometheus target이 `UP`이 되려면 앱 서버가 기본 포트에서 실행 중이어야 한다.

```text
Management Server: 8080
Redirect Server: 8081
Worker Server: 8082
```

API 로그가 보이려면 Management Server나 Redirect Server에서 실제 API 요청이 발생해야 한다. Actuator 요청은 Prometheus scrape 노이즈를 줄이기 위해 access log에서 제외한다.

Access log writer는 기본 로컬 실행에서 bounded async queue를 사용한다. 요청 thread는 로그 라인을 큐에 넣고 바로 반환하며, 별도 daemon worker가 파일에 flush한다. 큐가 가득 차면 처리량 보호를 우선해 초과 로그 라인을 drop할 수 있으므로, 운영에서는 로그 유실 허용 범위와 drop count 관측을 함께 둔다.

## 수집 지표

- JVM 메모리와 thread
- HTTP request rate, status, latency histogram
- HikariCP connection pool
- Redirect cache lookup count: `short_url_redirect_cache_total{result="hit|miss"}`
- Redirect resolution outcome count: `short_url_redirect_resolution_total{outcome="found|not_found|gone"}`

## API Access Log

Access log는 JSON line 형식으로 기록한다.

```text
Management Server: logs/short-url-management-server/api-access.log
Redirect Server: logs/short-url-redirect-server/api-access.log
```

기본 로그 필드:

- `timestamp`
- `application`
- `method`
- `uri`
- `path`
- `query`
- `status`
- `status_family`
- `duration_ms`
- `remote_addr`
- `user_agent`
- `trace_id`
- `metric`
- `metrics.duration_ms`
- `metrics.status`

로컬 `bootRun`은 앱 모듈 디렉터리를 기준으로 실행되므로 기본 설정은 `../../logs/...`를 사용한다. 다른 실행 위치에서는 `SHORT_URL_ACCESS_LOG_PATH` 환경변수로 파일 경로를 명시할 수 있다.

```bash
SHORT_URL_ACCESS_LOG_PATH=/absolute/path/api-access.log ./gradlew :apps:management-server:bootRun
```

Loki에서 직접 조회할 때는 다음 LogQL을 사용할 수 있다.

```logql
{log_type="api_access"} | json
```

특정 trace 조회:

```logql
{log_type="api_access"} | json | trace_id="..."
```

## 대시보드

Grafana dashboard 파일은 `monitoring/grafana/dashboards/` 아래에 있다. 로컬 Grafana가 시작될 때 provisioning 설정으로 자동 로드된다.

`Short URL Overview` 기본 패널:

- 실행 중인 앱 scrape target 수
- 앱별 HTTP request rate
- HTTP p95 latency
- HTTP status별 rate
- JVM memory used
- JVM live threads
- Hikari active/idle connections
- Redirect result와 cache hit/miss 5분 증가량

`Short URL API Logs` 기본 패널:

- 최근 API access log
- 상태 코드별 API request rate
- URL별 5분 요청 수 Top 10
- API p95 response time
- 4xx/5xx API log

## 운영 메모

- Redirect Server는 WebFlux지만 현재 JPA와 RedisTemplate 어댑터가 blocking이므로 HTTP latency와 boundedElastic saturation을 함께 관찰해야 한다.
- cache hit ratio가 낮고 Hikari active connection이 높아지면 Valkey TTL, negative/gone cache, hot key 분산 전략을 먼저 확인한다.
- p95 latency 패널은 `management.metrics.distribution.percentiles-histogram.http.server.requests=true` 설정에 의존한다.
- API Logs dashboard의 p95는 Loki의 `duration_ms` log field를 unwrap해서 계산한다.
- 운영 환경에서는 access log의 query string, user agent, remote address가 개인정보나 토큰을 포함하지 않는지 별도 마스킹 정책을 둔다.
- 운영 환경에서는 Grafana 기본 비밀번호를 환경변수나 secret으로 교체한다.
