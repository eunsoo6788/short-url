# Planning

## 목표

짧은 URL 생성, 조회, 리다이렉트, 이벤트 처리를 분리해 트래픽 성격에 맞는 서버를 독립적으로 운영할 수 있게 한다.

## 1차 범위

- Management Server
  - Short URL 생성
  - Short URL 단건 조회
  - 최근 Short URL 목록 조회
- Redirect Server
  - 짧은 코드 기반 302 리다이렉트
  - Valkey 캐시 우선 조회
  - cache miss 시 PostgreSQL fallback
  - negative cache와 gone cache 적용
  - per-code single-flight로 cache stampede 완화
  - 리다이렉트 이벤트 발행 포트 호출
- Worker Server
  - 이벤트 polling worker 골격
  - 이벤트 처리 유스케이스 골격
- 공통 모듈
  - 도메인 규칙
  - 저장소, 캐시, 메시징 포트
  - JPA/Redis/메시징 기본 어댑터
- Database
  - Flyway 기반 `short_links` 테이블 migration
  - Hibernate DDL `validate` 적용
- Observability
  - Actuator `/actuator/prometheus` endpoint 노출
  - Prometheus scrape 설정
  - Loki/Promtail 기반 API access log 수집
  - Grafana datasource/dashboard 자동 provisioning
  - redirect cache hit/miss와 resolution outcome 지표 수집
  - API 상태 코드, URL, 응답시간, trace id 로그 조회
- Load Testing
  - k6 smoke test
  - redirect read path 부하 테스트
  - 생성/리다이렉트 mixed 부하 테스트
  - VU, duration, 생성 개수, 생성 비율 환경변수화

## 제외한 범위

- 실제 Amazon SQS SDK 연동
- 인증/인가
- 클릭 통계 저장 모델
- 관리자 UI
- 운영용 캐시 무효화 정책
- 운영용 Grafana 인증/권한/알림 정책
- 운영용 로그 마스킹/보존/파기 정책

## 다음 단계 후보

- SQS 어댑터 구현과 LocalStack 테스트 추가
- 클릭 이벤트 저장소와 통계 API 추가
- URL 비활성화/삭제 API와 캐시 무효화 정책 추가
- custom alias 예약어 정책 추가
- multi-node cache stampede 완화가 필요해지면 Valkey 기반 distributed lock 또는 request coalescing 전용 계층 추가
- Grafana alert rule, SLO dashboard, 부하 테스트 리포트 추가
