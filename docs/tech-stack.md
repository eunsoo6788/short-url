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
- Flyway: database schema migration
- tsid-creator: time-sorted short code generation

## Infrastructure

- PostgreSQL
  - `modules:shortlink-persistence`에서 JPA 엔티티와 repository adapter를 제공한다.
  - 테이블 생성/수정/삭제는 Flyway migration으로만 수행한다.
- Elastic Cache Valkey
  - Redis protocol을 사용한다.
  - `short-url.cache.redis.enabled=true`일 때 `StringRedisTemplate` 기반 캐시를 사용한다.
  - 기본값은 로컬 테스트를 위한 in-memory cache다.
- Amazon SQS
  - 현재는 포트와 기본 로깅/No-op 어댑터만 제공한다.
  - 실제 AWS SDK 연동은 별도 어댑터로 추가한다.

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
- Management cache: in-memory
- Redirect cache: Valkey
- Database URL: `jdbc:postgresql://localhost:5432/short_url`
- JPA DDL: `validate`
- Flyway: enabled, `classpath:db/migration`

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

애플리케이션에서 실제 Valkey 캐시를 쓰려면 `short-url.cache.redis.enabled=true`를 설정한다. 기본값은 로컬 테스트 편의를 위해 in-memory cache다.

Redirect Server는 로컬 기본 설정에서 Valkey 캐시를 사용한다.

```properties
short-url.cache.redis.enabled=true
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
