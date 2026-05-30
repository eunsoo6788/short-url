# Architecture

`short-url`은 하나의 레포에서 세 개의 서버 앱과 공통 기능 모듈을 함께 관리하는 모듈러 모놀리스 구조다.

## 서버 구성

```mermaid
flowchart LR
    Users[Users]
    Redirect[Redirect Server<br/>WebFlux]
    Management[Management Server<br/>MVC]
    Worker[Worker Server]
    Cache[(Elastic Cache<br/>Valkey)]
    Database[(PostgreSQL)]
    Queue[Amazon SQS]

    Users --> Redirect
    Users --> Management
    Redirect --> Cache
    Redirect --> Database
    Redirect -.-> Queue
    Management --> Database
    Management --> Cache
    Management -.-> Queue
    Worker -.-> Queue
    Cache <--> Database
```

## Gradle 모듈

- `apps:management-server`: Short URL 생성/조회 관리 API. Spring MVC 기반.
- `apps:redirect-server`: 짧은 코드 리다이렉트 API. Spring WebFlux 기반.
- `apps:worker-server`: 리다이렉트 이벤트 소비와 후속 비동기 처리.
- `modules:shortlink-core`: 도메인 규칙, 유스케이스, 포트 인터페이스.
- `modules:shortlink-persistence`: PostgreSQL/JPA 저장소 어댑터와 Flyway migration.
- `modules:shortlink-cache`: Valkey/Redis 캐시 어댑터와 로컬 인메모리 캐시.
- `modules:shortlink-messaging`: SQS 메시징 포트의 기본 로깅/No-op 어댑터.
- `modules:common`: 공통 응답 모델.

## 의존 방향

앱 모듈은 필요한 기능 모듈에 의존한다. 기능 모듈은 `shortlink-core`의 포트에 맞춰 구현되며, core는 인프라 구현을 모른다.

```text
apps:* -> modules:shortlink-core
apps:* -> modules:shortlink-persistence/cache/messaging
modules:shortlink-persistence -> modules:shortlink-core
modules:shortlink-cache -> modules:shortlink-core
modules:shortlink-messaging -> modules:shortlink-core
```

## 리다이렉트 흐름

1. `GET /{code}` 요청이 `redirect-server`로 들어온다.
2. `RedirectResolver`가 `ShortLinkCache`에서 redirect cache entry를 먼저 조회한다.
3. `FOUND` cache hit이면 DB 조회 없이 원본 URL로 redirect한다.
4. `NOT_FOUND` cache hit이면 PostgreSQL 조회 없이 404를 반환한다.
5. `GONE` cache hit이면 PostgreSQL 조회 없이 410을 반환한다.
6. 캐시 미스면 per-code single-flight lock 안에서 cache를 다시 확인한 뒤 PostgreSQL을 조회한다.
7. 조회 결과에 따라 `FOUND`, `NOT_FOUND`, `GONE`을 TTL과 함께 Valkey에 저장한다.
8. 정상 redirect일 때만 리다이렉트 이벤트를 `RedirectEventPublisher`로 발행한다.
9. HTTP 302 응답으로 원본 URL 위치를 내려준다.

WebFlux 서버에서 JPA와 RedisTemplate 기반 호출은 blocking 작업이므로 컨트롤러에서 `boundedElastic` 스케줄러로 격리한다.

자세한 캐싱 정책은 [cache-strategy.md](/Users/eunsoojin/IdeaProjects/short-url/docs/cache-strategy.md)를 기준으로 한다.

## 관리 흐름

1. `POST /api/v1/short-links`로 원본 URL과 선택적 custom code를 받는다.
2. 도메인에서 URL과 code 규칙을 검증한다.
3. custom code가 없으면 TSID 기반 code를 생성하고 중복 여부를 확인한다.
4. PostgreSQL에 트랜잭션으로 저장한 뒤 redirect 서버 기준 short URL을 응답한다.
5. 코드 중복은 애플리케이션 사전 검사와 DB primary key 제약조건으로 이중 방어한다.

## Short Code 생성

- 기본 자동 생성기는 `TsidShortCodeGenerator`다.
- TSID 문자열은 시간 정렬 가능한 13자 문자열이며 `ShortCode` 규칙을 통과한다.
- `customCode`가 들어오면 TSID 생성 없이 사용자가 지정한 code를 검증 후 사용한다.
- JPA가 ID를 자동 생성하지 않는다. public short code는 애플리케이션 유스케이스에서 먼저 만든 뒤 저장한다.

## 데이터베이스 스키마 관리

- 테이블 생성, 수정, 삭제는 Flyway migration으로만 수행한다.
- Hibernate DDL 자동 생성은 사용하지 않고 `validate`로 스키마 정합성만 확인한다.
- 애플리케이션 데이터 insert, update, delete는 Flyway migration 범위에 넣지 않는다.
- 최초 migration은 `modules:shortlink-persistence/src/main/resources/db/migration/V1__create_short_links.sql`이다.

## 워커 흐름

현재 워커는 `RedirectEventConsumer`에서 이벤트를 가져와 `RedirectEventProcessor`에 넘기는 골격을 제공한다. 실제 Amazon SQS 연동은 `modules:shortlink-messaging`에 SQS 어댑터를 추가하는 방식으로 확장한다.
