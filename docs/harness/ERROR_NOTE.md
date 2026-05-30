# Harness Error Note

이 문서는 `short-url` 프로젝트를 개발하면서 반복하지 말아야 할 실수와 그 예방책을 기록하는 하네스 오답 노트다.

## 기록 규칙

- 기능 개발, 버그 수정, 리팩터링 후 반드시 한 번 확인한다.
- 실제 실수뿐 아니라 설계 판단이 바뀐 이유도 기록한다.
- 다음 개발자가 바로 활용할 수 있도록 짧고 구체적으로 쓴다.
- 같은 유형의 문제가 반복되면 예방 체크리스트로 승격한다.

## 오답 노트 템플릿

```markdown
## YYYY-MM-DD - 제목

- 상황:
- 오판:
- 원인:
- 수정:
- 예방:
- 관련 파일:
```

## 예방 체크리스트

- 개발 전 사용자에게 계획을 공유하고 동의를 받았는가?
- 모듈 경계와 패키지 책임을 침범하지 않았는가?
- 도메인 규칙이 컨트롤러나 영속성 코드로 새지 않았는가?
- 짧은 코드 충돌, 만료, 잘못된 URL 같은 경계 조건을 테스트했는가?
- 리다이렉트 성능에 영향을 주는 동기 작업을 추가하지 않았는가?
- 성능 지표가 의미별로 분리되어 dashboard에서 오해 없이 읽히는가?
- `/docs` 아래 개발 참고 문서를 갱신했는가?

## 기록

## 2026-05-31 - Spring Boot 4 EntityScan 패키지 변경

- 상황: JPA 모듈에서 `EntityScan`을 기존 Spring Boot 3 계열 import로 사용했다.
- 오판: Spring Boot 4에서도 `org.springframework.boot.autoconfigure.domain.EntityScan`이 유지될 것이라고 가정했다.
- 원인: Spring Boot 4에서 persistence 관련 API가 `spring-boot-persistence`로 분리되었다.
- 수정: `org.springframework.boot.persistence.autoconfigure.EntityScan`으로 변경했다.
- 예방: Spring Boot 4 신규 모듈 API를 사용할 때는 로컬 Gradle cache의 jar package를 확인하고 import를 확정한다.
- 관련 파일: `modules/shortlink-persistence/src/main/kotlin/toy/two/shorturl/shortlink/persistence/ShortLinkPersistenceConfiguration.kt`

## 2026-05-31 - WebFlux 서버의 blocking adapter 격리

- 상황: Redirect Server는 WebFlux지만 현재 저장소는 JPA, Redis 캐시는 `StringRedisTemplate` 기반이다.
- 오판: WebFlux 컨트롤러에서 blocking adapter를 그대로 호출하면 이벤트 루프를 막을 수 있다.
- 원인: 초기 구조에서 인프라 adapter를 reactive로 완전히 분리하지 않았다.
- 수정: `RedirectController`에서 `Mono.fromCallable`과 `Schedulers.boundedElastic()`로 blocking 유스케이스 호출을 격리했다.
- 예방: Redirect Server에 새로운 adapter를 붙일 때 reactive/non-blocking 여부를 먼저 확인한다.
- 관련 파일: `apps/redirect-server/src/main/kotlin/toy/two/shorturl/redirect/RedirectController.kt`

## 2026-05-31 - 캐시 무효화 정책은 다음 단계로 분리

- 상황: 리다이렉트 캐시는 만료 시각 기반 TTL은 처리하지만 URL 비활성화/삭제 API가 아직 없다.
- 오판: 생성/리다이렉트만 있는 1차 범위에서 무효화 API까지 함께 넣으면 범위가 과도하게 커진다.
- 원인: 현재 기획 범위가 서버 구조와 기본 Short URL 흐름 구현에 집중되어 있다.
- 수정: 비활성화/삭제와 캐시 무효화는 후속 작업으로 문서화했다.
- 예방: URL 상태 변경 API를 추가할 때 cache evict 포트를 먼저 설계한다.
- 관련 파일: `docs/planning.md`, `modules/shortlink-core/src/main/kotlin/toy/two/shorturl/shortlink/application/RedirectResolver.kt`

## 2026-05-31 - 로컬 PostgreSQL 설정과 앱 datasource 일치

- 상황: Docker Compose로 로컬 PostgreSQL을 추가했다.
- 오판: Compose의 DB 이름, 계정, 포트를 앱 설정과 다르게 두면 서버는 떠도 DB 연결에서 실패한다.
- 원인: 인프라 설정은 여러 파일에 분산되기 쉽다.
- 수정: `docker-compose.yml`의 `short_url` DB, `short_url` 계정, `5432` 포트를 앱 `application.properties`와 맞췄다.
- 예방: DB 설정을 바꿀 때는 Compose, Management Server, Redirect Server datasource를 함께 확인한다.
- 관련 파일: `docker-compose.yml`, `apps/management-server/src/main/resources/application.properties`, `apps/redirect-server/src/main/resources/application.properties`

## 2026-05-31 - Codex PostgreSQL MCP는 Docker 기반으로 실행

- 상황: Codex에 PostgreSQL MCP 서버를 추가했다.
- 오판: 공식 `npx @modelcontextprotocol/server-postgres` 설정을 바로 쓰면 될 것으로 봤다.
- 원인: 현재 머신의 셸 PATH에는 `npm`과 `npx`가 없고, Docker Catalog의 `mcp/postgres` 이미지는 일반 pull이 막혀 있었다.
- 수정: 공개 pull 가능한 `docker.io/acuvity/mcp-server-postgres:0.3.0` 이미지를 사용하고 `DATABASE_URI` 환경변수로 연결하도록 설정했다.
- 예방: MCP 서버를 추가할 때는 `command`가 현재 Codex 실행 환경에서 실제로 존재하는지 먼저 확인한다.
- 관련 파일: `/Users/eunsoojin/.codex/config.toml`, `docs/tech-stack.md`

## 2026-05-31 - Valkey는 Redis 호환 설정으로 연결

- 상황: Docker Compose에 로컬 Valkey 서버를 추가했다.
- 오판: Valkey라는 이름 때문에 Spring 쪽 별도 클라이언트 설정이 필요하다고 착각할 수 있다.
- 원인: Valkey는 Redis protocol 호환 서버이고, 현재 애플리케이션 캐시 어댑터는 Spring Data Redis 기반이다.
- 수정: Compose는 `valkey/valkey:8-alpine`을 사용하고 `6379` 포트를 열었다. 앱은 `short-url.cache.redis.enabled=true`일 때 RedisTemplate 기반 어댑터로 연결한다.
- 예방: 캐시 설정을 바꿀 때는 Compose 포트, Spring Data Redis 기본 포트, `short-url.cache.redis.enabled` 값을 함께 확인한다.
- 관련 파일: `docker-compose.yml`, `modules/shortlink-cache/src/main/kotlin/toy/two/shorturl/shortlink/cache/ShortLinkCacheConfiguration.kt`, `docs/tech-stack.md`

## 2026-05-31 - Spring Boot 4 Flyway 자동설정 모듈 누락

- 상황: Flyway dependency와 migration SQL을 추가했지만 Management Server 기동 시 Hibernate `validate`가 먼저 실패했다.
- 오판: `flyway-core`만 classpath에 있으면 Spring Boot 4에서도 자동설정이 같이 로드될 것이라고 봤다.
- 원인: Spring Boot 4는 Flyway 자동설정이 `spring-boot-flyway` 모듈로 분리되어 있다.
- 수정: Management Server와 Redirect Server에 `org.springframework.boot:spring-boot-flyway` 의존성을 추가했다.
- 예방: Spring Boot 4에서 기술별 자동설정은 `spring-boot-*` 모듈이 필요한지 공식 auto-configuration 문서와 로컬 jar를 확인한다.
- 관련 파일: `build.gradle`, `apps/management-server/src/main/resources/application.properties`, `apps/redirect-server/src/main/resources/application.properties`

## 2026-05-31 - Redirect negative cache와 gone cache 분리

- 상황: 기존 캐시는 원본 URL만 저장해서 없는 code와 만료 code의 반복 요청을 DB가 계속 맞을 수 있었다.
- 오판: 정상 redirect URL만 캐시해도 충분하다고 보면 대용량 트래픽에서 무작위 code scan이나 만료 code 재요청이 DB 부하로 이어진다.
- 원인: 캐시 엔트리가 `FOUND`만 표현했다.
- 수정: `FOUND`, `NOT_FOUND`, `GONE` cache entry를 도입하고 각각 TTL을 분리했다.
- 예방: read path cache를 설계할 때 성공 응답뿐 아니라 반복 실패 응답의 DB 부하도 같이 모델링한다.
- 관련 파일: `modules/shortlink-core/src/main/kotlin/toy/two/shorturl/shortlink/application/port/ShortLinkCache.kt`, `modules/shortlink-core/src/main/kotlin/toy/two/shorturl/shortlink/application/RedirectResolver.kt`, `docs/cache-strategy.md`

## 2026-05-31 - Valkey 장애 시 redirect fail-open

- 상황: RedisTemplate 기반 캐시 호출 실패가 redirect 요청 실패로 전파될 수 있었다.
- 오판: 캐시가 항상 정상이라는 가정은 redirect 서비스의 가용성을 낮춘다.
- 원인: 캐시는 성능 최적화 계층인데, 실패 처리를 하지 않으면 핵심 DB fallback 경로보다 먼저 장애를 만든다.
- 수정: Redis cache adapter에서 read 실패는 cache miss로 처리하고 write/evict 실패는 경고 로그만 남기게 했다.
- 예방: redirect read path의 외부 의존성은 가능한 fail-open 또는 격리 전략을 먼저 검토한다.
- 관련 파일: `modules/shortlink-cache/src/main/kotlin/toy/two/shorturl/shortlink/cache/RedisShortLinkCache.kt`, `docs/cache-strategy.md`

## 2026-05-31 - Public short code는 JPA ID 자동생성에 맡기지 않기

- 상황: short code 생성 전략을 랜덤 Base62에서 TSID 기반으로 바꿨다.
- 오판: JPA/Hibernate ID generator로 생성하면 충분하다고 볼 수 있지만, short code는 API 응답과 cache key에 바로 필요한 public identifier다.
- 원인: persistence concern과 도메인 public identifier 생성 책임이 섞일 수 있다.
- 수정: `TsidShortCodeGenerator`를 애플리케이션 계층의 `ShortCodeGenerator` 구현으로 두고, `ShortLinkCreator`가 저장 전에 code를 만든다.
- 예방: code 생성 전략을 바꿀 때는 `ShortCode` 문자셋/길이, DB PK 길이, cache key 포맷, API 응답을 함께 확인한다.
- 관련 파일: `modules/shortlink-core/src/main/kotlin/toy/two/shorturl/shortlink/application/TsidShortCodeGenerator.kt`, `modules/shortlink-core/src/main/kotlin/toy/two/shorturl/shortlink/config/ShortLinkCoreConfiguration.kt`

## 2026-05-31 - Redirect 결과 지표와 cache 지표 분리

- 상황: Grafana dashboard에 redirect 결과와 cache hit/miss를 한 metric 안의 outcome tag로 섞어 표현하려 했다.
- 오판: 하나의 counter에 모든 이벤트를 넣으면 단순해 보이지만, `cache_hit`과 `not_found`가 같은 차원의 결과처럼 보여 운영자가 잘못 해석할 수 있다.
- 원인: metric cardinality를 줄이는 것에만 집중해 지표 의미의 상호 배타성을 놓쳤다.
- 수정: `short_url_redirect_cache_total{result}`와 `short_url_redirect_resolution_total{outcome}`으로 분리했다.
- 예방: Prometheus metric을 만들 때 metric name, label, counter 증가 조건이 같은 의미 차원인지 먼저 점검한다.
- 관련 파일: `apps/redirect-server/src/main/kotlin/toy/two/shorturl/redirect/RedirectMicrometerMetricsRecorder.kt`, `monitoring/grafana/dashboards/short-url-overview.json`

## 2026-05-31 - Docker Prometheus가 로컬 JVM 앱을 scrape할 때 host.docker.internal 사용

- 상황: Prometheus는 Docker container에서 실행되고 앱 서버는 호스트 JVM에서 실행된다.
- 오판: Prometheus 설정에 `localhost:8080`을 넣으면 호스트 앱을 scrape할 수 있다고 보기 쉽다.
- 원인: container 내부의 localhost는 container 자신을 가리킨다.
- 수정: scrape target을 `host.docker.internal:8080`, `:8081`, `:8082`로 지정했다.
- 예방: Docker container에서 호스트 프로세스로 접근하는 설정은 항상 네트워크 namespace를 기준으로 검증한다.
- 관련 파일: `monitoring/prometheus/prometheus.yml`, `docs/observability.md`

## 2026-05-31 - bootRun 기준 access log 상대 경로

- 상황: API access log를 `logs/...` 상대 경로로 설정했더니 Promtail이 보는 레포 루트가 아니라 `apps/management-server/logs/...`에 파일이 생성됐다.
- 오판: Gradle `bootRun` 실행 시 `user.dir`이 레포 루트일 것이라고 가정했다.
- 원인: Spring Boot Gradle plugin의 `bootRun`은 앱 모듈 디렉터리를 작업 디렉터리로 사용한다.
- 수정: 기본 access log 경로를 `../../logs/...`로 변경하고, 실행 위치가 다르면 `SHORT_URL_ACCESS_LOG_PATH`로 override할 수 있게 했다.
- 예방: Docker bind mount가 읽는 파일 경로와 JVM이 쓰는 파일 경로는 실제 실행 방식으로 end-to-end 검증한다.
- 관련 파일: `apps/management-server/src/main/resources/application.properties`, `apps/redirect-server/src/main/resources/application.properties`, `monitoring/promtail/promtail.yml`

## 2026-05-31 - API 호출 단위 로그는 Prometheus metric이 아니라 Loki log로 분리

- 상황: Grafana에서 상태값, 응답시간, URL, trace id가 보이는 API 호출 단위 화면이 필요했다.
- 오판: Prometheus metric만 확장하면 요청 단위 상세 조회까지 해결된다고 보기 쉽다.
- 원인: Prometheus는 집계/시계열에 강하고, 개별 요청 payload 성격의 고카디널리티 데이터에는 맞지 않는다.
- 수정: Prometheus dashboard는 집계 지표로 유지하고, JSON access log는 Promtail과 Loki로 수집해 별도 `Short URL API Logs` dashboard에서 조회한다.
- 예방: 관측성 요구사항을 metric, log, trace 중 어떤 신호로 풀지 먼저 나눈 뒤 도구를 붙인다.
- 관련 파일: `monitoring/grafana/dashboards/short-url-api-logs.json`, `monitoring/loki/loki.yml`, `monitoring/promtail/promtail.yml`

## 2026-05-31 - README 온보딩 문서는 실제 포트와 대시보드 URL 기준으로 작성

- 상황: 신규 팀원이 처음 실행할 때 앱 포트, Docker Compose 서비스, Grafana dashboard URL, API 테스트 파일을 한 곳에서 확인해야 했다.
- 오판: 세부 문서가 `/docs` 아래에 있으면 README는 짧아도 충분하다고 보기 쉽다.
- 원인: 처음 합류한 팀원은 문서 구조 자체를 아직 모르므로 실행 순서와 접속 URL을 찾는 비용이 크다.
- 수정: README에 Quick Start, 개발 도구 URL, 아키텍처, 테스트, 관측성, 참고 문서 링크를 정리했다.
- 예방: 인프라 포트나 dashboard UID를 바꿀 때 README와 `/docs`의 URL 표를 함께 갱신한다.
- 관련 파일: `README.md`, `docker-compose.yml`, `monitoring/grafana/dashboards/short-url-api-logs.json`

## 2026-05-31 - k6 컨테이너는 localhost 대신 host.docker.internal 사용

- 상황: k6를 Docker Compose service로 실행해 호스트에서 떠 있는 Spring Boot 앱을 부하 테스트해야 했다.
- 오판: k6 script의 기본 target을 `localhost:8080`으로 두면 앱에 닿을 것이라고 보기 쉽다.
- 원인: Docker container 안의 localhost는 container 자신을 가리킨다.
- 수정: k6 기본 `MANAGEMENT_BASE_URL`, `REDIRECT_BASE_URL`을 `host.docker.internal`로 설정하고, Linux 호환을 위해 compose에 `host-gateway` extra host를 추가했다.
- 예방: Docker container 기반 테스트 도구가 호스트 앱을 호출할 때는 network namespace 기준으로 target URL을 확인한다.
- 관련 파일: `docker-compose.yml`, `load-tests/k6/short-url-smoke.js`, `load-tests/k6/redirect-load.js`, `load-tests/k6/mixed-load.js`, `docs/load-testing.md`
