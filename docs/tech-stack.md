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
- Nginx: redirect edge cache

## Infrastructure

- PostgreSQL
  - `modules:shortlink-persistence`에서 JPA 엔티티와 repository adapter를 제공한다.
  - 테이블 생성/수정/삭제는 Flyway migration으로만 수행한다.
  - Docker Compose에서 primary `localhost:5432`, replica `localhost:5433`으로 실행한다.
  - `short-url.datasource.replication.enabled=true`이면 `@Transactional(readOnly=true)` read query는 replica로, write query와 migration은 primary로 라우팅한다.
- Elastic Cache Valkey
  - Redis protocol을 사용한다.
  - `short-url.cache.redis.enabled=true`일 때 Caffeine local cache miss 이후 `StringRedisTemplate` 기반 remote cache를 사용한다.
  - Docker Compose에서 master `localhost:6379`, replicas `localhost:6380`, `localhost:6381`로 실행한다.
  - `short-url.cache.redis.replication.enabled=true`이면 Lettuce `REPLICA_PREFERRED`로 read는 replica 우선, write는 master로 처리한다.
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
- Nginx cache
  - Docker Compose에서 `short-url-nginx-cache`로 실행한다.
  - `localhost:8088`로 들어온 redirect 요청을 `host.docker.internal:8081` Redirect Server로 proxy한다.
  - 301/302 응답은 30초, 404/410 응답은 10초 cache한다.
  - `proxy_cache_lock`으로 같은 cache key의 동시 miss를 하나의 upstream 요청으로 합친다.
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
- Database replica URL: `jdbc:postgresql://localhost:5433/short_url`
- Valkey master: `localhost:6379`
- Valkey replicas: `localhost:6380`, `localhost:6381`
- JPA DDL: `validate`
- Flyway: enabled, `classpath:db/migration`
- Prometheus: `http://localhost:9090`
- Loki: `http://localhost:3100`
- Grafana: `http://localhost:3000`, `admin/admin`
- Nginx redirect cache: `http://localhost:8088`

로컬에서 PostgreSQL 없이 빠르게 테스트할 때는 `./gradlew test`를 사용한다. 서버 실행은 PostgreSQL 준비 후 진행한다.

## Local PostgreSQL

로컬 개발용 PostgreSQL은 Docker Compose로 실행한다. 기본 구성은 primary 1대와 streaming replica 1대다.

```bash
docker compose up -d postgres postgres-replication-setup postgres-replica
docker compose ps postgres postgres-replica
```

접속 정보는 애플리케이션 기본 설정과 동일하다.

```text
primary host: localhost
primary port: 5432
replica host: localhost
replica port: 5433
database: short_url
username: short_url
password: short_url
```

접속 확인:

```bash
docker compose exec -T postgres psql -U short_url -d short_url -c "select current_database(), current_user;"
docker compose exec -T postgres-replica psql -U short_url -d short_url -c "select pg_is_in_recovery();"
```

## Local Valkey

로컬 개발용 Valkey는 Docker Compose로 실행한다. 기본 구성은 master 1대와 replica 2대다. Valkey는 Redis protocol과 호환되므로 Spring Data Redis 설정으로 접속한다.

```bash
docker compose up -d valkey valkey-replica-1 valkey-replica-2
docker compose ps valkey valkey-replica-1 valkey-replica-2
docker compose exec -T valkey valkey-cli ping
docker compose exec -T valkey-replica-1 valkey-cli info replication
```

접속 정보:

```text
master: localhost:6379
replica-1: localhost:6380
replica-2: localhost:6381
protocol: Redis-compatible
```

애플리케이션에서 실제 Valkey remote cache를 쓰려면 `short-url.cache.redis.enabled=true`를 설정한다. Redis master-replica 라우팅은 `short-url.cache.redis.replication.enabled=true`로 켠다. Redis를 비활성화하면 Caffeine local cache만 사용한다. Management Server도 생성 성공 시 redirect negative cache를 evict할 수 있도록 Valkey에 연결한다.

Redirect Server와 Management Server는 로컬 기본 설정에서 Valkey master-replica cache와 PostgreSQL read replica routing을 사용한다.

```properties
short-url.cache.redis.enabled=true
short-url.cache.redis.replication.enabled=true
short-url.cache.redis.replication.master.host=localhost
short-url.cache.redis.replication.master.port=6379
short-url.cache.redis.replication.replicas[0].host=localhost
short-url.cache.redis.replication.replicas[0].port=6380
short-url.cache.redis.replication.replicas[1].host=localhost
short-url.cache.redis.replication.replicas[1].port=6381
short-url.cache.local.maximum-size=100000
short-url.cache.stampede.lock-ttl=3s
short-url.cache.stampede.wait-timeout=500ms
short-url.cache.stampede.poll-interval=20ms
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=100ms
short-url.datasource.replication.enabled=true
short-url.datasource.replication.replica.url=jdbc:postgresql://localhost:5433/short_url
short-url.datasource.replication.replica.username=short_url
short-url.datasource.replication.replica.password=short_url
spring.datasource.hikari.connection-timeout=500
spring.datasource.hikari.validation-timeout=500
spring.transaction.default-timeout=2s
short-url.access-log.async-enabled=true
short-url.access-log.queue-capacity=65536
short-url.access-log.flush-every-lines=256
```

PostgreSQL replica read는 eventual consistency다. 방금 생성한 short code를 replica가 아직 받지 못한 아주 짧은 순간에는 redirect miss가 발생할 수 있고, 이 서비스는 miss도 짧게 negative cache한다. 운영에서는 replica lag 모니터링, negative TTL 조정, 필요 시 redirect miss의 primary fallback을 함께 검토한다.

Management Server는 oversized/slow create request가 request parsing 전에 자원을 오래 붙잡지 않도록 Servlet/Tomcat 경계에도 제한을 둔다.

```properties
short-url.management.create.max-request-body-bytes=8192
short-url.management.create.max-drain-body-bytes=0
server.tomcat.connection-timeout=2s
server.tomcat.keep-alive-timeout=5s
server.tomcat.max-keep-alive-requests=100
server.tomcat.max-swallow-size=64KB
```

Redirect Server는 긴 URI/query가 로그 직렬화와 Netty request parsing 경로를 과도하게 점유하지 않도록 별도 guard를 둔다.

```properties
short-url.redirect.request.max-uri-chars=4096
short-url.redirect.request.max-query-chars=2048
server.netty.connection-timeout=2s
server.netty.idle-timeout=5s
server.netty.max-keep-alive-requests=100
server.netty.max-initial-line-length=8KB
```

Access log는 bounded async queue를 통해 request thread와 파일 쓰기 지연을 분리한다. 큐가 가득 차면 서버 처리량을 지키기 위해 초과 로그 라인을 drop할 수 있으므로 운영에서는 drop count와 로그 유실 허용 범위를 함께 본다.

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

## Local Nginx Cache

Redirect Server 앞단 Nginx cache는 Docker Compose로 실행한다. 앱 서버는 기존처럼 host에서 `./gradlew :apps:redirect-server:bootRun`으로 실행하고, Nginx 컨테이너가 `host.docker.internal:8081`로 proxy한다.

```bash
docker compose up -d nginx-cache
curl -i http://localhost:8088/{shortCode}
```

응답에는 cache 상태를 확인할 수 있는 header가 붙는다.

```text
X-Cache-Status: MISS | HIT | BYPASS | EXPIRED | STALE | UPDATING
```

기본 정책:

```text
cache key: scheme + method + host + uri
301/302: 30s
404/410: 10s
proxy_cache_lock: on
```

query string은 cache key에서 제외한다. Redirect Server가 `/{code}` path만으로 원본 URL을 결정하기 때문에 `/abc?utm=1`과 `/abc?utm=2`를 같은 short code 요청으로 합쳐 upstream 중복 요청을 줄이기 위함이다.

## Local Load Testing

k6 부하 테스트는 Docker Compose service로 실행한다.

```bash
docker compose run --rm k6 run /scripts/short-url-smoke.js
docker compose run --rm -e VUS=100 -e DURATION=5m -e SHORT_URL_COUNT=1000 k6 run /scripts/redirect-load.js
docker compose run --rm -e VUS=50 -e DURATION=3m -e CREATE_RATIO=0.10 k6 run /scripts/mixed-load.js
```

자세한 시나리오와 threshold 기준은 [load-testing.md](/Users/eunsoojin/IdeaProjects/short-url/docs/load-testing.md)를 참고한다.
