# Cache Strategy

## 목표

Redirect Server의 read path는 대용량 트래픽에서 가장 먼저 압박을 받는다. 캐시는 PostgreSQL 보호, redirect latency 감소, hot key 처리 안정성을 목표로 한다.

## 패턴

현재 전략은 Caffeine local cache와 Valkey/Redis remote cache를 함께 쓰는 2단 cache-aside/read-through에 가깝다.

1. Redirect Server가 Caffeine local cache에서 code를 조회한다.
2. local cache hit이면 Redis와 DB를 조회하지 않는다.
3. local cache miss이면 같은 JVM 안에서 code별 single-flight lock을 잡고 Valkey에서 `short-url:redirect:{code}`를 조회한다.
4. Redis cache hit이면 DB를 조회하지 않고, Redis의 남은 TTL만큼 local cache에 backfill한다.
5. Redis cache miss이면 Redis 기반 cache load lock을 시도한다.
6. lock 획득에 실패하면 다른 인스턴스가 cache를 채우는 중이라고 보고 짧게 polling 대기한다.
7. 대기 중 cache가 채워지면 DB 조회 없이 응답한다.
8. lock을 획득했거나 대기 후에도 cache miss이면 PostgreSQL을 조회한다.
9. 조회 결과를 local cache와 Valkey에 저장한 뒤 응답한다.

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
- local cache 최대 엔트리 수: `100000`
- cache load lock TTL: `3s`
- cache fill 대기 시간: `500ms`
- cache fill polling 간격: `20ms`

만료 시간이 있는 Short URL은 `min(defaultTtl, expiresAt까지 남은 시간)`을 사용한다. TTL jitter는 동일한 시점에 많은 hot key가 동시에 만료되는 상황을 줄이기 위해 TTL을 약간 짧게 분산한다.

Redis hit으로 local cache를 채울 때는 Redis key의 남은 TTL을 사용한다. 이렇게 해야 local cache가 remote cache보다 더 오래된 값을 들고 있는 상황을 줄일 수 있다.

## Cache Stampede 완화

현재 구현은 두 단계로 stampede를 완화한다.

1. Local remote-read single-flight
   - Caffeine local cache miss가 동시에 많이 발생해도 같은 JVM 안에서는 code별로 하나의 요청만 Redis를 조회한다.
   - 나머지 요청은 lock 안에서 local cache를 다시 확인하므로 cold local cache 상황에서 Redis hot key 부하를 줄인다.

2. Redis distributed cache load lock
   - Redis까지 miss인 경우 `short-url:redirect:lock:{code}` lock을 `SET NX PX`로 획득한다.
   - lock을 획득한 인스턴스만 PostgreSQL을 조회하고 cache를 채운다.
   - lock 획득에 실패한 인스턴스는 짧게 cache fill을 기다린 뒤 cache가 채워지면 DB 조회 없이 응답한다.
   - lock release는 Lua script로 token을 비교한 뒤 삭제한다. lock TTL이 있어 owner 장애 시에도 lock이 영구히 남지 않는다.

Redis lock 장애 시에는 fail-open으로 동작한다. 즉, lock을 잡지 못했다는 이유로 redirect를 실패시키지 않고 DB fallback을 허용한다.

## Hot Key 완화

Hot key는 Caffeine local cache가 1차로 흡수한다. 이미 각 Redirect Server 인스턴스의 local cache에 올라온 key는 Redis와 DB를 거치지 않는다.

local cache가 비어 있거나 만료된 순간에도 remote-read single-flight가 Redis 조회를 JVM당 하나로 제한한다. 여러 인스턴스가 동시에 cold start 하더라도 Redis 조회는 인스턴스당 하나로 줄고, Redis miss 이후 DB 조회는 distributed cache load lock으로 줄인다.

완전히 제거할 수 없는 범위:

- Redis 자체에 이미 존재하는 매우 뜨거운 key는 cold start 시 인스턴스 수만큼 Redis 조회가 발생할 수 있다.
- lock 대기 시간보다 DB 조회가 오래 걸리면 일부 요청은 fail-open으로 DB까지 내려갈 수 있다.

운영에서 hot key가 더 커지면 local cache pre-warming, stale-while-revalidate, CDN/edge cache, 인기 code 별도 pinning을 추가로 검토한다.

## 장애 처리

Valkey 장애가 redirect 장애로 바로 전파되지 않게 Redis cache adapter는 fail-open으로 동작한다. local cache hit은 Valkey 장애와 무관하게 그대로 응답한다.

- cache read 실패: cache miss로 보고 PostgreSQL fallback을 수행한다.
- cache write 실패: 경고 로그를 남기고 redirect 응답은 계속 진행한다.
- cache evict 실패: 경고 로그를 남긴다.

이 정책은 가용성을 우선한다. 단, Valkey 장애 중에는 local cache에 없는 key가 PostgreSQL까지 내려가므로 운영 환경에서는 DB connection pool, rate limit, circuit breaker, cache 복구 알림을 함께 둔다. 로컬 설정은 Redis command timeout을 짧게 둬서 Redis 지연이 redirect worker thread를 오래 붙잡지 않게 한다.

## 무효화 정책

Short URL 생성이 성공하면 해당 code의 redirect cache를 evict한다. 이 처리는 custom code 생성 직전에 같은 code가 negative cache로 저장되어 있던 경우 stale 404가 TTL 동안 유지되는 상황을 줄인다.

현재 1차 범위에는 URL 수정, 비활성화, 삭제 API가 없다. 해당 API를 추가할 때는 DB update와 cache evict를 같은 유스케이스에 묶는다.

권장 순서:

1. DB 상태 변경
2. `ShortLinkCache.evict(code)`
3. 필요하면 변경 이벤트 발행

DB commit 전 cache를 먼저 삭제하면 rollback 시 cache와 DB가 어긋날 수 있다. 트랜잭션 commit 이후 evict가 더 안전하다.

## 이벤트 처리

Redirect event는 cache hit 여부와 관계없이 정상 redirect일 때만 발행한다. 클릭 집계나 분석 처리는 SQS/Worker로 비동기화해 redirect latency에 영향을 주지 않게 한다.
