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
