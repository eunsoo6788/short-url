# Cache Strategy

## 목표

Redirect Server의 read path는 대용량 트래픽에서 가장 먼저 압박을 받는다. 캐시는 PostgreSQL 보호, redirect latency 감소, hot key 처리 안정성을 목표로 한다.

## 패턴

현재 전략은 cache-aside/read-through에 가깝다.

1. Redirect Server가 Valkey에서 `short-url:redirect:{code}`를 조회한다.
2. cache hit이면 DB를 조회하지 않는다.
3. cache miss이면 PostgreSQL을 조회한다.
4. 조회 결과를 Valkey에 저장한 뒤 응답한다.

## 캐시 엔트리

`ShortLinkCache`는 세 가지 상태를 저장한다.

- `FOUND`: 원본 URL을 포함한다. redirect 가능 상태다.
- `NOT_FOUND`: code가 존재하지 않는다. 짧은 TTL로 negative cache한다.
- `GONE`: code는 존재하지만 만료되었거나 비활성이다. 짧은 TTL로 cache한다.

이렇게 나누면 없는 code나 만료 code로 들어오는 반복 요청이 PostgreSQL까지 매번 내려가지 않는다.

## TTL

- 기본 redirect TTL: `10m`
- 없는 code negative TTL: `30s`
- 만료 또는 비활성 TTL: `1m`
- TTL jitter: `10%`

만료 시간이 있는 Short URL은 `min(defaultTtl, expiresAt까지 남은 시간)`을 사용한다. TTL jitter는 동일한 시점에 많은 hot key가 동시에 만료되는 상황을 줄이기 위해 TTL을 약간 짧게 분산한다.

## Cache Stampede 완화

현재 구현은 cache miss 시 같은 JVM 안에서 code별 single-flight lock을 잡는다. 동일 code로 동시에 들어온 요청 중 하나만 PostgreSQL을 조회하고, 나머지는 lock 안에서 cache를 다시 확인한다.

현재 방식의 범위:

- 단일 Redirect Server 인스턴스 안에서는 효과가 있다.
- 여러 Redirect Server 인스턴스 사이에는 적용되지 않는다.

다중 인스턴스에서 같은 hot key stampede가 문제가 되면 Valkey 기반 distributed lock 또는 별도 request coalescing 계층을 추가한다.

## 장애 처리

Valkey 장애가 redirect 장애로 바로 전파되지 않게 Redis cache adapter는 fail-open으로 동작한다.

- cache read 실패: cache miss로 보고 PostgreSQL fallback을 수행한다.
- cache write 실패: 경고 로그를 남기고 redirect 응답은 계속 진행한다.
- cache evict 실패: 경고 로그를 남긴다.

이 정책은 가용성을 우선한다. 단, Valkey 장애 중에는 PostgreSQL 부하가 증가하므로 운영 환경에서는 DB connection pool, rate limit, circuit breaker, cache 복구 알림을 함께 둔다.

## 무효화 정책

현재 1차 범위에는 URL 수정, 비활성화, 삭제 API가 없다. 해당 API를 추가할 때는 DB update와 cache evict를 같은 유스케이스에 묶는다.

권장 순서:

1. DB 상태 변경
2. `ShortLinkCache.evict(code)`
3. 필요하면 변경 이벤트 발행

DB commit 전 cache를 먼저 삭제하면 rollback 시 cache와 DB가 어긋날 수 있다. 트랜잭션 commit 이후 evict가 더 안전하다.

## 이벤트 처리

Redirect event는 cache hit 여부와 관계없이 정상 redirect일 때만 발행한다. 클릭 집계나 분석 처리는 SQS/Worker로 비동기화해 redirect latency에 영향을 주지 않게 한다.
