# Tech Stack

## Runtime

- Java 21
- Kotlin 2.2.21
- Gradle 9.5.1

## Framework

- Spring Boot 4.0.6
- Spring MVC: Management Server
- Spring WebFlux: Redirect Server
- Spring Scheduling: Worker Server
- Spring Data JPA: PostgreSQL persistence
- Spring Data Redis: Valkey/Redis protocol cache
- Caffeine: redirect local cache
- Flyway: database schema migration
- tsid-creator: time-sorted short code generation
- Spring Boot Actuator: health, metrics, prometheus endpoint
- Micrometer Prometheus registry: Prometheus metrics export
- Loki + Promtail: API access log collection
- k6: load testing

## Infrastructure

- PostgreSQL
  - `modules:shortlink-persistence`에서 JPA 엔티티와 repository adapter를 제공한다.
  - 테이블 생성/수정/삭제는 Flyway migration으로만 수행한다.
- Elastic Cache Valkey
  - Redis protocol을 사용한다.
  - `short-url.cache.redis.enabled=true`일 때 Caffeine local cache miss 이후 `StringRedisTemplate` 기반 remote cache를 사용한다.
  - Redis cache hit은 남은 TTL만큼 Caffeine local cache에 backfill한다.
  - Redis 비활성화 시에는 Caffeine local cache만 사용한다.
- Amazon SQS
  - 현재는 포트와 기본 로깅/No-op 어댑터만 제공한다.
  - 실제 AWS SDK 연동은 별도 어댑터로 추가한다.
- Prometheus
  - Docker Compose에서 `short-url-prometheus`로 실행한다.
  - 로컬 앱 서버의 `/actuator/prometheus` endpoint를 scrape한다.
- Grafana
  - Docker Compose에서 `short-url-grafana`로 실행한다.
  - Prometheus/Loki datasource와 `Short URL Overview`, `Short URL API Logs` dashboard를 provisioning한다.
- Loki
  - Docker Compose에서 `short-url-loki`로 실행한다.
  - API access log를 저장하고 LogQL 조회를 제공한다.
- Promtail
  - Docker Compose에서 `short-url-promtail`로 실행한다.
  - 로컬 `logs/` 디렉터리의 JSON line access log를 tailing한다.
- k6
  - Docker Compose profile `load-test`의 ephemeral service로 실행한다.
  - `load-tests/k6` 아래 smoke, redirect, mixed 부하 테스트 스크립트를 사용한다.

## Test

- Kotlin test + JUnit 5
- 도메인 유스케이스 단위 테스트
- TSID code 생성 테스트
- MVC 컨트롤러 단위 테스트
- WebFlux 컨트롤러 단위 테스트
- Worker polling 단위 테스트
- Flyway migration 테스트

## Local Defaults

- Management Server: `8080`
- Redirect Server: `8081`
- Management cache: Caffeine local cache
- Redirect cache: Caffeine local cache + Valkey
- Database URL: `jdbc:postgresql://localhost:5432/short_url`
- JPA DDL: `validate`
- Flyway: enabled, `classpath:db/migration`
- Prometheus: `http://localhost:9090`
- Loki: `http://localhost:3100`
- Grafana: `http://localhost:3000`, `admin/admin`

로컬에서 PostgreSQL 없이 빠르게 테스트할 때는 `./gradlew test`를 사용한다. 서버 실행은 PostgreSQL 준비 후 진행한다.

## Local PostgreSQL

로컬 개발용 PostgreSQL은 Docker Compose로 실행한다.

```bash
docker compose up -d postgres
docker compose ps postgres
```

접속 정보는 애플리케이션 기본 설정과 동일하다.

```text
host: localhost
port: 5432
database: short_url
username: short_url
password: short_url
```

접속 확인:

```bash
docker compose exec -T postgres psql -U short_url -d short_url -c "select current_database(), current_user;"
```

## Local Valkey

로컬 개발용 Valkey는 Docker Compose로 실행한다. Valkey는 Redis protocol과 호환되므로 Spring Data Redis 설정으로 접속한다.

```bash
docker compose up -d valkey
docker compose ps valkey
docker compose exec -T valkey valkey-cli ping
```

접속 정보:

```text
host: localhost
port: 6379
protocol: Redis-compatible
```

애플리케이션에서 실제 Valkey remote cache를 쓰려면 `short-url.cache.redis.enabled=true`를 설정한다. Redis를 비활성화하면 Caffeine local cache만 사용한다. Management Server도 생성 성공 시 redirect negative cache를 evict할 수 있도록 Valkey에 연결한다.

Redirect Server는 로컬 기본 설정에서 Valkey 캐시를 사용한다.

```properties
short-url.cache.redis.enabled=true
short-url.cache.local.maximum-size=100000
short-url.cache.stampede.lock-ttl=3s
short-url.cache.stampede.wait-timeout=500ms
short-url.cache.stampede.poll-interval=20ms
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

캐시 TTL 기본값:

```properties
short-url.cache.redirect.default-ttl=10m
short-url.cache.redirect.negative-ttl=30s
short-url.cache.redirect.gone-ttl=1m
short-url.cache.redirect.ttl-jitter-ratio=0.1
```

## Codex PostgreSQL MCP

Codex 로컬 설정 파일은 `/Users/eunsoojin/.codex/config.toml`이다. 현재 PostgreSQL MCP는 Docker 기반 stdio 서버로 연결한다.

```toml
[mcp_servers.postgres]
command = "docker"
args = [
  "run",
  "-i",
  "--rm",
  "--read-only",
  "-e",
  "DATABASE_URI",
  "docker.io/acuvity/mcp-server-postgres:0.3.0"
]
startup_timeout_sec = 60

[mcp_servers.postgres.env]
DATABASE_URI = "postgresql://short_url:short_url@host.docker.internal:5432/short_url"
```

Codex 앱 또는 스레드를 재시작하면 `postgres` MCP 서버가 로드된다.

## Local Monitoring

로컬 성능 모니터링과 API 로그 조회는 Prometheus, Loki, Promtail, Grafana를 Docker Compose로 실행한다.

```bash
docker compose up -d prometheus loki promtail grafana
```

Prometheus scrape target:

```text
short-url-management: host.docker.internal:8080/actuator/prometheus
short-url-redirect: host.docker.internal:8081/actuator/prometheus
short-url-worker: host.docker.internal:8082/actuator/prometheus
```

Redirect Server custom metrics:

```text
short_url_redirect_cache_total{result="hit|miss"}
short_url_redirect_resolution_total{outcome="found|not_found|gone"}
```

API access log files:

```text
logs/short-url-management-server/api-access.log
logs/short-url-redirect-server/api-access.log
```

Promtail labels:

```text
log_type=api_access
job=short-url-api-management
job=short-url-api-redirect
```

자세한 대시보드와 운영 메모는 [observability.md](/Users/eunsoojin/IdeaProjects/short-url/docs/observability.md)를 참고한다.

## Local Load Testing

k6 부하 테스트는 Docker Compose service로 실행한다.

```bash
docker compose run --rm k6 run /scripts/short-url-smoke.js
docker compose run --rm -e VUS=100 -e DURATION=5m -e SHORT_URL_COUNT=1000 k6 run /scripts/redirect-load.js
docker compose run --rm -e VUS=50 -e DURATION=3m -e CREATE_RATIO=0.10 k6 run /scripts/mixed-load.js
```

자세한 시나리오와 threshold 기준은 [load-testing.md](/Users/eunsoojin/IdeaProjects/short-url/docs/load-testing.md)를 참고한다.
