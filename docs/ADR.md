# Architecture Decision Records

구현 경계와 신뢰 원천은 [아키텍처](ARCHITECTURE.md), 제품 범위는 [PRD](PRD.md)를 참조한다. 이 문서는 결정 당시의 이유와 트레이드오프를 보존한다.

Accepted 상태는 결정의 상태다. 현재 구현과 차이가 있으면 두 사실을 구분한다. 예를 들어 발송 로그의 현재 키는 알림 종류와 대상 식별자를 포함하며, 과거 복약 전용 키에서 확장되었다. 결정별 본문은 당시 근거로 유지한다.

## 철학
- 민감 데이터와 결제·인증 경계에서는 빠른 구현보다 검증 가능성, idempotency, rollback 가능성을 우선한다.
- 외부 SDK는 격리하고, 도메인 모델은 외부 API와 framework 세부사항을 모르게 한다.
- 단일 운영자가 장애를 좁힐 수 있도록 실패 모드와 관측 지점을 코드·문서에 남긴다.

---

### ADR-001: Spring Boot 단일 API 서버 유지
**Status**: Accepted

**결정**: 모바일 앱 백엔드는 Spring Boot 단일 서버로 유지하고, 인증·채팅·복약·알림·구독을 모듈 패키지로 분리한다.

**이유**: 현재 운영 규모에서 서비스 분리는 배포·관측·transaction 추적 비용을 키운다. 단일 서버는 1인 운영자가 장애 범위를 빠르게 좁힐 수 있다.

**트레이드오프**: 모듈 간 compile-time coupling이 생길 수 있다. 이를 `archTest`, 하위 AGENTS.md, package boundary로 제어한다.

**검증**: `./gradlew test archTest`.

---

### ADR-002: 관계 데이터는 MySQL, 채팅 메시지는 MongoDB에 분리 저장
**Status**: Accepted

**결정**: 사용자·토큰·복약·알림 로그·구독은 MySQL/JPA에 저장하고, 채팅 메시지는 MongoDB document로 저장한다.

**이유**: 결제·인증·복약은 unique constraint와 transaction이 중요하다. 채팅 메시지는 문서형 payload와 대화 이력 조회가 중심이다.

**트레이드오프**: 한 요청에서 두 저장소 일관성을 완전 transaction으로 묶기 어렵다. 서비스 계층에서 실패 순서와 보상 가능성을 명시해야 한다.

**검증**: repository/service 테스트와 H2 + embedded Mongo test profile.

---

### ADR-003: 알림은 AFTER_COMMIT 이벤트와 idempotent send log로 처리
**Status**: Accepted

**결정**: 복약 변경 이후 알림 예약은 transaction commit 후 이벤트로 수행하고, `(bundle_id, scheduled_date, device_token_id)` unique key로 중복 발송을 막는다.

**이유**: DB rollback된 복약 계획에 대해 push가 발송되면 사용자 신뢰가 깨진다. Cloud Tasks/polling 재시도는 중복 가능성이 있으므로 DB dedup이 필요하다.

**트레이드오프**: at-least-once에 가까운 구조라 중복 방어 코드가 필요하고, task enqueue 실패 시 별도 복구 경로가 필요하다.

**검증**: notification service/scheduler tests, `NotificationSendLogRepositoryTest`.

---

### ADR-004: App Store 구독 상태는 Apple 검증 결과와 서버 DB만 신뢰
**Status**: Accepted

**결정**: 클라이언트가 보낸 구독 상태·만료일·transaction id는 권한 판단에 사용하지 않고, Apple JWS/API 검증 결과를 DB에 저장해 조회한다.

**이유**: 결제 권한은 클라이언트에서 위조될 수 있다. 서버 검증과 idempotent 저장이 중복 권한 부여와 권한 누락을 줄인다.

**트레이드오프**: Apple API 장애 시 즉시 권한 갱신이 실패할 수 있다. timeout/5xx는 최대 2회 retry 후 pending/실패로 응답한다.

**검증**: subscription service/dto tests, JWS validator tests 추가 시 우선순위 높음.

---

### ADR-005: 운영 secret은 환경·CI secret 주입으로만 다룬다
**Status**: Accepted

**결정**: JWT, Apple, Firebase, OpenAI, DB, Sentry secret은 코드와 문서에 원문 저장하지 않고 환경변수, GitHub Secrets, Secret Manager, base64 주입으로만 전달한다.

**이유**: 인증·결제·push credential 유출은 즉시 사용자 데이터와 비용 리스크로 이어진다.

**트레이드오프**: local/dev/prod property가 늘어나고 boot 실패 원인이 환경 설정에 숨을 수 있다. test profile에는 dummy 값을 명시한다.

**검증**: config tests, workflow secret scope review, 로그/Sentry scrubber 확인.

---

### ADR-006: develop dev 배포 워크플로 제거
**Status**: Accepted

**결정**: 삭제된 EC2 테스트 서버를 대상으로 하던 `develop` dev 배포 워크플로 `mori-dev-cicd.yml`을 제거한다. 새 dev 런타임을 provision하기 전까지 develop 브랜치에는 배포 대상을 연결하지 않는다.

**이유**: 배포 대상이 없는 상태에서 `develop` merge마다 Docker Hub push, AWS security group 변경, EC2 SSH 배포를 시도하면 CI 신뢰도와 credential 노출면만 커진다.

**트레이드오프**: `develop` merge 후 자동 통합 환경 검증과 dev Docker image push는 사라진다. 대신 PR test gate와 local 검증을 유지하고, dev 런타임을 다시 만들 때 배포 계약을 재정의한다.

**검증**: dev deploy workflow removal review, GitHub Actions PR test gate 유지 확인.

---

### ADR-007: 이메일 기반 임시 로그인 제거와 공개 인증 경로 제한
**Status**: Accepted

**결정**: 이메일만으로 사용자 자격을 발급하던 임시 로그인과 전용 발급·갱신 코드를 제거한다. 사용자 인증 경로 중 인증 없이 허용하는 요청은 `POST /api/v1/auth/apple`과 `POST /api/v1/auth/refresh`로 한정하고, 로그아웃과 그 밖의 인증 경로에는 인증을 요구한다.

**이유**: 이메일은 계정 소유 증거가 아니다. 환경 제한 없는 임시 로그인은 기존 사용자 사칭과 비활성 계정 복구를 허용하므로, 외부 연결 재개 전에 해당 신뢰 우회 경로를 없앤다.

**버린 대안**: 개발 프로필에서만 임시 로그인을 유지하는 방안은 잘못된 프로필 활성화와 향후 재노출 위험을 남긴다. 단순 경로 차단만 하는 방안도 전용 자격 발급 구현을 남기므로 선택하지 않는다.

**트레이드오프**: 이메일만으로 로그인하던 개발 도구와 클라이언트는 정식 로그인이나 테스트 대역을 사용해야 한다. 기존에 발급한 자격의 일괄 폐기는 이번 코드 제거에 포함하지 않으며, 제거 자체가 과거 자격을 무효화하지는 않는다. Apple 신원 증거의 발급자·대상 검증 보완은 별도 작업이다.

**검증**: `RemovedTempLoginSecurityTest`에서 실제 인증 필터를 거쳐 미인증 임시 로그인 거절, 인증된 임시 로그인 경로 부재와 갱신 자격 미생성, 정식 공개 경로 유지를 확인한다. `./gradlew test lint archTest build`로 전체 회귀와 패키징을 검사한다.

---

### ADR-008: 복약 예약 의도의 원자적 저장과 버전별 Cloud Tasks 등록
**Status**: Accepted

**결정**: 복약 계획 변경과 `medication_schedule_outbox`를 같은 MySQL 트랜잭션에 저장한다. 커밋 이후 이벤트는 처리를 깨우는 신호로 유지하고, OIDC로 보호한 복구 요청이 대기·재시도·만료된 처리권을 다시 처리한다. 발송 시점 실행은 Cloud Tasks가 담당한다. ADR-003의 AFTER_COMMIT·발송 중복 방지 결정을 유지하면서 예약 등록 전 유실 구간을 보완한다.

**이유**: 계획 저장 이후 프로세스 종료나 예약망 장애가 발생해도 등록 의도를 보존해야 한다. 삭제한 Task ID를 바로 재사용하면 예약이 없어질 수 있어, 같은 계획·날짜·변경 버전의 등록은 하나의 불변 식별자로 수렴시키고 다른 변경에는 새 식별자를 사용한다.

**버린 대안**: DB 작업 큐에서 발송 시각·재시도를 전부 실행하는 방식은 현재 Cloud Tasks 실행 계약을 다시 구현해야 한다. 메모리 이벤트 재시도만 추가하는 방식은 프로세스 종료를 복구하지 못한다. 일기와 복약의 범용 작업 프레임워크를 먼저 만드는 방식은 서로 다른 갱신·실패 정책을 불필요하게 결합한다.

**트레이드오프**: 등록 완료와 기기별 발송 완료를 별도로 관측해야 하며 외부 주기 복구 호출 연결이 필요하다. 작업은 원자적 선점·5분 처리권·소유자 확인을 사용하고 최초 포함 최대 4번, 1분·5분·15분 간격으로 등록한다. 실패 종료 작업은 보존하고 인증된 명시적 재시도를 허용한다. 미리 생성한 미래 날짜 작업도 계획 변경 시 새 버전으로 갱신한다.

**발송 정책**: 처음 저장할 때 이미 지난 시각은 예약하지 않는다. 미래 예약 등록이 지연된 경우 같은 서울 날짜 안에서는 늦게 등록하고 날짜가 지나면 만료한다. 같은 계획·날짜·기기당 발송 중복 키는 변경하지 않는다. 이전 버전과 삭제된 계획은 발송 전에 거절하지만 이미 시작한 FCM 호출의 취소나 exactly-once 전달은 보장하지 않는다. 기존 버전 없는 Task는 계획 버전이 0일 때만 허용한다.

**변경·보상**: V13은 버전 열과 Outbox 테이블을 추가한다. 현재 운영 연결이 없는 개발 복사본이며 클라우드 자원 생성·배포는 하지 않는다. 배포·복구·호환 버전으로의 rollback은 [운영 절차](./runbooks/medication-outbox.md)를 따른다. 기존 계획 버전을 초기화하거나 등록 기록을 임의 삭제하는 보상은 하지 않는다.

**검증**: `MedicationOutboxIntegrationTest`에서 실제 저장·롤백, 중복 배치·선점 경쟁, 처리권 회수, 동일 ID 재시도와 소진을 검사한다. 발송 버전·날짜·기존 중복 키와 내부 OIDC 거절은 알림 테스트로 검사한다. MySQL Flyway migration과 `./gradlew test lint archTest build integrationTest`를 검증 대상으로 둔다.

---

### ADR-009: 채팅 MongoDB 트랜잭션과 영속 Saga 보상·복구
**Status**: Accepted

**결정**: 기존 MongoDB 세션·메시지 컬렉션과 단계 핸들러·REST 경로를 유지한다. 프로필 변경이 없으면 MongoDB 트랜잭션으로 메시지와 세션을 함께 확정한다. 프로필 변경이 있으면 MongoDB에 임시 대화를 저장하고, MySQL 프로필과 커밋 표시를 함께 확정한 뒤 MongoDB 대화를 공개한다. 작업 조정자가 판정·보상·재처리를 맡는 오케스트레이션형 Saga를 응용한다. ADR-002의 저장소 역할을 유지하면서 실패 처리 결정을 보완한다. 세션은 현행 코드상 MongoDB 문서이며, 과거 아키텍처의 MySQL 세션 표기를 정정한다.

**이유**: MySQL flush를 먼저 수행해도 MongoDB의 두 번째 메시지·세션 쓰기 실패와 MySQL 최종 commit 실패는 남는다. MongoDB 트랜잭션은 내부 일부 저장을 막지만 두 DB의 원자성을 제공하지 않는다. 프로필과 같은 SQL 트랜잭션의 커밋 표시가 있어야 응답 유실·프로세스 종료 후 SQL 결과를 판정할 수 있다. 미확정 대화를 숨겨두면 보상 시 최신 공개 상태를 과거 값으로 덮어쓸 필요가 없다.

**진행 기록과 경쟁 제어**: MySQL에 원문 없는 작업 기록·사용자 작업권을 둔다. 복구자는 SQL 잠금 읽기와 MongoDB 사용자별 작업권의 조건부 쓰기로 이전 실행자를 차단한다. 새 세션·자가 복구·삭제도 같은 작업권을 사용한다. 삭제 후에도 MongoDB 작업권 문서는 남겨 늦은 쓰기의 세션 부활을 막는다. 회원 초기화·삭제의 SQL 트랜잭션이 끝날 때까지 작업권 잠금을 유지한다.

**준비와 부수 효과**: AI·단계 처리에는 분리된 User·세션 사본을 사용한다. SQL 쓰기 직전에 최신 User를 잠금 조회·refresh하고 실제 변경 필드만 적용한다. 활성 여부도 refresh 뒤 확인한다. Hibernate 연결은 트랜잭션 종료 후 반환하도록 명시한다. 알림 예약·취소는 대화 확정 뒤에 수행하며, 알림 실패가 대화 확정을 취소하지 않게 한다.

**다른 프로필 쓰기와의 경합**: User는 `@DynamicUpdate`로 실제 변경 열만 갱신한다. 오래된 닉네임 수정이 채팅 프로필을 덮어쓰는 경로를 차단한다. 초기화는 작업권 확보 뒤 최신 User를 잠금 refresh해, 오래된 엔티티의 dirty 판정 때문에 필드 초기화가 빠지는 경합도 막는다.

**버린 대안**: 채팅 원문을 MySQL로 이전하고 Outbox로 MongoDB를 복제하는 안은 현재 선택한 저장소 역할과 변경 범위를 크게 바꾼다. MongoDB 트랜잭션만 추가하는 안은 SQL 최종 커밋 실패를 해결하지 못한다. 메모리 try/catch 보상만 사용하는 안은 프로세스 종료와 보상 실패를 복구하지 못한다. 전체 세션 스냅샷을 되돌리는 안은 다른 작업의 변경을 덮어쓸 수 있다.

**트레이드오프**: 일반 MongoDB 대화도 공통 작업권을 위해 SQL 메타데이터를 쓴다. 프로필 변경에는 임시 저장·확정의 추가 쓰기가 있다. 두 DB의 순간적인 전역 원자성을 보장하지 않으며 장애 중에는 미확정 작업과 사용자별 쓰기 차단을 허용한다. 자동 복구는 실행 중인 프로세스와 제한된 재시도에 의존한다. 클라이언트의 동일 POST 재전송 식별, 메모리 알림의 재시작 복원, 외부 푸시의 정확히 한 번 전달, 회원 삭제 전체의 교차 DB 원자성은 별도 범위다.

**변경·보상**: V14 SQL migration과 V15 멱등 Java migration으로 작업 기록·MongoDB 인덱스를 준비한다. 과거 메시지는 상태 필드 없이 읽고 세션의 누락 버전은 0으로 처리한다. PENDING을 이해하지 못하는 구버전으로 되돌리기 전에 미완료 작업을 정리해야 한다. 현재 운영 연결은 없으며 실제 전환·복구 호출·수동 점검 연결은 [채팅 복구 절차](./runbooks/chat-recovery.md)를 따른다.

**검증**: 실제 MySQL·MongoDB replica set의 `ChatSagaFaultTest`에서 다섯 프로필 단계, SQL 거부·롤백·성공 후 응답 유실, MongoDB 일부 쓰기·확정·보상 오류, 복구 경쟁, 독립 JVM 종료·새 프로세스 복구, 기존 문서·조회·알림 순서·OSIV 연결 점유를 검사한다. 조건 주입 결과를 운영 오류율로 사용하지 않는다. 전체 단위·정적·아키텍처·빌드 검사와 선택형 `chatFaultTest`를 구분한다.
