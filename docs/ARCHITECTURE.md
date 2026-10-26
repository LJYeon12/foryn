# 아키텍처

이 문서는 현재 코드의 모듈 경계, 데이터 흐름과 신뢰 원천을 설명한다. 제품 범위는 [PRD](PRD.md), 선택 근거는 [ADR](ADR.md), 문서 충돌 시 우선순위는 [문서 작성 규칙](AGENTS.md#문서별-책임과-우선순위)을 따른다.

## 전체 구조

Spring Boot API 서버가 모바일 앱의 요청을 처리한다. MySQL은 계정·접근 자격·복약·일기·알림 로그·구독과 채팅 복구 메타데이터를 저장한다. MongoDB는 대화 세션·메시지와 사용자별 쓰기 작업권을 저장한다. 두 저장소의 쓰기를 하나의 원자적 트랜잭션으로 간주하지 않는다.

~~~mermaid
flowchart LR
    App[모바일 앱] --> API[요청과 인증 경계]
    API --> Services[기능 정책과 저장·외부 호출 조정]
    Services --> SQL[관계 데이터]
    Services --> Messages[대화 세션·메시지]
    Services --> Apple[Apple 신원·결제]
    Services --> AI[생성형 응답]
    Services --> Jobs[예약 작업]
    Jobs --> Internal[내부 호출 인증]
    Internal --> Push[기기별 푸시]
~~~

## 모듈 경계

| 영역 | 책임 | 구현 |
| --- | --- | --- |
| 인증 | Apple 로그인, 접근·갱신 자격, 거부 목록, 내부 OIDC 인증 | `src/main/java/com/forA/chatbot/auth` |
| 계정 | 프로필, 초기화·비활성화, 일기 알림 설정 조회 | `src/main/java/com/forA/chatbot/user` |
| 대화 | 세션과 단계 전이, 메시지, 행동 기술과 생성 응답 | `src/main/java/com/forA/chatbot/chat` |
| 일기 | 선택 날짜의 초안·완료, 만료 정리, 코멘트, 태그, 알림 설정·재예약 작업 | `src/main/java/com/forA/chatbot/diary` |
| 복약 | 계획·기록, 연속 실천 이벤트 | `src/main/java/com/forA/chatbot/medications` |
| 알림 | 기기 토큰, 발송 처리권, 작업 예약·재예약, 푸시 | `src/main/java/com/forA/chatbot/notification` |
| 리포트 | 기간별 복약·감정 집계, 일기 태그 요약 캐시 | `src/main/java/com/forA/chatbot/report` |
| 구독 | 외부 결제 검증, 서버 알림, 구독 상태 | `src/main/java/com/forA/chatbot/subscriptions` |
| 공통·설정 | 보안 설정, 외부 클라이언트 구성, 날짜 제공, 오류·지표 | `src/main/java/com/forA/chatbot/config`, `src/main/java/com/forA/chatbot/global` |

## 계층 규칙

Controller는 입력과 인증 사용자 식별자를 받고 Service로 전달한다. Service는 정책·트랜잭션·외부 호출을 조정하고 Repository는 저장소 접근을 담당한다. Controller에서 Repository를 직접 호출하지 않는다. Domain/entity에서 외부 SDK를 호출하지 않으며 Converter는 응답·입력 조립에 집중한다.

이 규칙은 개발 시 지켜야 할 경계다. 규칙의 존재가 모든 보안 조건의 구현 완료를 의미하지는 않는다.

## 주요 데이터 흐름

| 기능 | 흐름 |
| --- | --- |
| 정식 로그인 | Apple 신원 증거 검증 → 사용자 조회·생성 → 접근·갱신 자격 발급 |
| 대화 | 소유권·작업권 확인 → 트랜잭션 밖 입력·응답 준비 → 프로필 변경 여부별 저장 → 대화 확정·후속 알림 |
| 일기 | 날짜·입력 검증 → 만료 초안 별도 정리 → 선택 날짜 초안 생성·과거 초안 갱신 → 점수·태그로 같은 기록 완료 |
| 일기 알림 | 설정 전체 교체와 job 저장 → 커밋 이후 재예약 → 내부 인증 → 기기별 선점·발송·완료 기록 |
| 복약 알림 | 계획·Outbox 함께 저장 → 커밋 이후 등록 시도·외부 복구 호출 → 버전별 Cloud Tasks → 현재 버전 검사·기기별 발송 |
| 월간 감정 | 대화·복약·완료 일기 점수 조회 → 일기에 2배 가중치 → 날짜별 평균 → 조회일 기준 최근 이틀 태그 요약 |
| 구독 | 외부 상태 조회·서명 검증 → 서버 구독 갱신 → 상태 조회 |

세부 동작은 [기능 지도](../.cluedoc/README.md)를 따른다. 한줄일기 API 명세는 현재 복사본에 포함되어 있지 않으므로 현재 요청 형식은 해당 Controller·DTO로 확인한다.

## 신뢰 원천

| 영역 | 상태를 판단하는 근거 |
| --- | --- |
| 인증 | Apple 검증 결과, 서버 JWT·refresh token 저장소와 SecurityContext |
| 채팅 | MongoDB의 확정된 ChatSession·ChatMessage. 프로필 변경의 커밋 판정은 같은 MySQL 트랜잭션에 저장한 작업 표시를 사용하며 임시 대화와 확정 이력을 구분 |
| 일기 | 소유자·선택 날짜에 연결된 `diaries`의 초안·완료 상태. 초안 만료는 대상 날짜가 아닌 마지막 수정 시각으로 판단 |
| 복약 | medication bundle·item·log의 DB 상태와 unique constraint |
| 알림 발송 | NotificationSendLog의 중복 방지 키와 FCM·Cloud Tasks 처리 결과 |
| 복약 예약 | 계획의 변경 버전과 함께 저장한 Outbox 상태. 설정 저장·Cloud Tasks 등록·기기별 발송 완료를 구분 |
| 일기 예약 | 저장된 알림 설정과 재예약 job 상태. 설정 저장·예약 완료·기기 전달 완료를 구분 |
| 감정 리포트 | 대화·복약 자료와 월간에만 포함하는 완료 일기 점수. 월간 요약 캐시는 사용자·조회일 기준 |
| 구독 | Apple App Store JWS·API 결과와 subscriptions DB 상태 |

## 트랜잭션과 이벤트 경계

채팅의 JPA 기본 관리자는 `transactionManager`이고 MongoDB 관리자는 `mongoTransactionManager`다. 핸들러는 분리된 User·세션 작업 사본을 변경한다. MySQL 쓰기에서는 최신 사용자 정보를 다시 잠금 조회·갱신하고, 변경된 프로필 필드만 적용한다. 응답 준비 중에는 트랜잭션을 열지 않는다. Hibernate 연결은 트랜잭션 종료 후 반환하도록 명시해 OSIV가 있는 경우도 검사한다.

User의 SQL UPDATE도 실제 변경 필드로 한정해, 오래된 닉네임 수정이 채팅에서 바꾼 프로필을 덮어쓰지 않게 한다. 회원 초기화는 대화 작업권 확보 뒤 사용자 정보를 잠금 refresh한 다음 초기화한다. 변경되지 않았다고 잘못 판단한 필드가 초기화에서 빠지지 않도록 하기 위해서다.

~~~mermaid
flowchart LR
    Prepare[단계 처리·응답 준비] --> Delta{프로필 변경?}
    Delta -->|없음| Mongo[Mongo 메시지·세션 함께 확정]
    Delta -->|있음| Stage[Mongo 임시 메시지·다음 상태 저장]
    Stage --> SQL[프로필과 커밋 표시 함께 확정]
    SQL --> Publish[Mongo 대화 확정]
    SQL -->|롤백 확인| Cancel[해당 작업 임시 자료 정리]
    Publish --> Finish[완료 기록·작업권 해제]
    Mongo --> Finish
    Finish --> Effects[알림 예약·취소]
~~~

교차 DB 경로는 오케스트레이션형 Saga를 응용한다. SQL 트랜잭션 본문에서 프로필 flush와 Mongo 임시 저장을 수행하고 SQL 커밋 뒤에 대화를 공개한다. 확실한 SQL 롤백은 해당 작업의 임시 자료만 보상하고, SQL 성공은 저장된 응답을 확정하는 방향으로 복구한다. 재시작 복구는 작업 기록·잠금 읽기·두 저장소의 fence를 사용한다. 일반 조회는 임시 메시지를 제외하고 과거 상태 필드 없는 메시지를 포함한다. 클라이언트의 동일 POST 재전송을 식별하는 요청 키는 제공하지 않는다.

새 세션·자가 복구·세션 삭제도 같은 사용자 작업권과 MongoDB 트랜잭션을 사용한다. 회원 초기화·삭제는 상위 SQL 트랜잭션 종료까지 작업권을 잠근다. 알림 예약·취소는 대화 확정 뒤에 실행하며, 기존 메모리 예약의 재시작 보존은 별도 범위다. 상세 상태·실행·제한은 [채팅 복구 절차](./runbooks/chat-recovery.md)를 따른다.

~~~mermaid
sequenceDiagram
    participant API as 알림 설정 요청
    participant DB as 기능 상태와 작업
    participant Worker as 재예약 처리
    participant Queue as 외부 예약망
    API->>DB: 설정과 재예약 작업 함께 저장
    DB-->>API: 저장 결과
    DB-->>Worker: 커밋 이후 이벤트
    Worker->>DB: 실행 가능한 작업 선점
    Worker->>Queue: 복약은 버전별 등록, 일기는 기존 예약 교체
    Worker->>DB: 성공 또는 재시도 상태 기록
~~~

- 일기 만료 정리는 별도 트랜잭션으로 확정하므로 이후 생성·완료 요청이 실패해도 정리 결과는 유지된다.
- 복약 계획 변경과 Outbox는 원자적으로 저장한다. 커밋 이후 이벤트는 등록을 깨우고 외부 OIDC 복구 호출은 실행 대기와 5분 이상 처리권을 복구한다. 최초 포함 최대 4회 등록하며 재시도 간격은 1분·5분·15분이다.
- 같은 계획·날짜·버전은 같은 Task ID를 사용하고 이전 Task를 삭제·재사용하지 않는다. 발송 시 현재 버전을 확인하며 발송 중복 키에는 예약 버전을 추가하지 않는다. 과거 날짜 작업은 만료하고, 이미 시작한 외부 전송은 취소하지 못한다.
- 일기 재예약은 최초 시도 후 최대 3회 재시도하며, 주기 검색으로 실행 대기와 오래된 처리 상태를 복구한다.
- 발송 로그는 알림 종류·대상·예정 날짜·기기로 선점한다. 외부 푸시 성공과 완료 기록 사이에 중단되면 재전달될 수 있다.
- 외부 결제 검증 결과를 구독에 반영하지만, 이벤트 순서 보존과 기능별 유료 권한 강제는 별도 검증 대상이다.

관련 구현은 일기·알림 Service, `DiaryTransactionExecutor`, `NotificationSendLogService`, `SubscriptionService`에 있다.

복약 예약 Outbox의 실행·복구·배포 계약은 [운영 절차](./runbooks/medication-outbox.md)를 따른다. Cloud Tasks의 재시도는 등록 이후 요청 처리에 적용되며, DB Outbox의 등록 재시도와 구분한다. 기존 polling 모드는 명시적으로 선택할 때만 동작하는 별도 경로이고 이번 Outbox 구현의 대상은 Cloud Tasks다.

## 날짜와 리포트 경계

| 기능 | 현재 기준 |
| --- | --- |
| 일기 날짜·만료 | 저장된 사용자 시간대, 유효하지 않으면 서울 |
| 월간 감정의 월·요약 조회일 | 저장된 사용자 시간대 |
| 일기 알림 | 사용자 현지 시각을 예약 instant로 변환 |
| 주간 리포트 | 서버 기본 날짜 |
| 복약 계획 시각 | 서울 기준 입력·표시와 UTC 저장 변환 |

로그인·토큰 갱신으로 사용자 시간대를 자동 갱신하는 경로는 구현되어 있지 않다. 신규 계정은 서울 기본값을 사용한다. 날짜별 동작은 [일기와 인사이트](../.cluedoc/journaling-and-insights/README.md)에서 설명한다.

## 실행 환경과 외부 연동

기존 운영을 중단한 뒤 현재 공유본에서 운영 연결과 자격증명을 제거했다. 현재 연결된 운영 환경과 자동 배포는 없다. 기존 운영에서 사용한 외부 연동과 배포 구조는 아래와 같이 보존한다. 운영을 재개할 때에는 대상 환경의 연결·자격증명을 주입하고 연동을 다시 검증한다.

| 외부 서비스 | 보존한 역할 |
| --- | --- |
| OpenAI | AI 대화·일기 코멘트 등 생성형 응답 |
| Firebase FCM | 기기별 푸시 알림 전달 |
| GCP Cloud Tasks | 복약·일기 알림 예약과 내부 엔드포인트 호출 |
| Apple 로그인·App Store | 사용자 인증·구독 검증과 서버 알림 |
| Sentry | 런타임 오류 추적 |

기존 배포 구조는 로컬 Docker Compose, 개발 EC2·Docker Hub, 운영 GCP Cloud Run·Cloud SQL·MongoDB·Cloud Tasks였다. Prometheus·Grafana와 Sentry를 통한 관측 구조도 보존한다. ADR의 배포 결정은 당시 환경의 근거로 읽는다.

원본 저장소에 보존된 운영 배포 정의는 main 변경 시 GitHub Actions에서 Docker 이미지를 빌드해 Artifact Registry로 푸시하고 Cloud Run 서비스를 배포한다. Cloud SQL 연결은 Java Connector와 런타임 서비스 계정의 IAM DB 인증을 사용한다. 애플리케이션 자격증명은 GitHub Secrets와 환경변수로 주입하던 구성이다. 현재 공유본에는 이 배포 workflow와 운영 설정을 포함하지 않는다.

운영 지표 수집은 독립 Prometheus 서버가 Cloud Run을 직접 조회하는 방식과 구분한다. 같은 Cloud Run 서비스의 Run GMP 사이드카가 `/actuator/prometheus`를 수집해 Google Managed Service for Prometheus로 보내고, 운영 Grafana가 이를 조회한다. `RunMonitoring` 수집 설정은 Secret Manager에서 사이드카에 마운트한다. Sentry는 런타임 오류를 추적한다. MongoDB·Grafana의 호스팅 위치는 보존된 자료에서 확정하지 않는다.

현재 로컬 구성은 `compose.yaml`로 MySQL·MongoDB·Prometheus·Grafana를 실행하며, Spring Boot는 호스트/IDE에서 실행한다. Prometheus는 호스트의 Actuator를 수집하고 수동 실행 k6의 remote write 결과를 받는다. Grafana는 같은 저장소를 조회한다. DB 스키마는 Flyway migration으로 반영하고 로컬 추가 설정에서 Hibernate는 검증만 수행한다. [로컬 실행](development/local-setup.md)과 [관측·부하 확인](development/observability.md)에서 절차를 설명한다.

검사 범위, PR·커밋 관문과 실행 명령은 [검증 안내](development/testing.md)에 둔다.

관계 DB 변경은 `src/main/resources/db/migration`의 SQL Flyway migration으로, MongoDB 준비는 `src/main/java/com/forA/chatbot/config/migration`의 Java Flyway migration으로 관리한다. 개인키·서비스 계정·로컬 설정은 소스와 빌드 리소스에서 제외한다. 런타임 오류 추적 코드는 유지하지만 과거 조직으로 소스를 업로드하던 빌드 플러그인은 없다.

`V16__Add_chat_session_report_index`는 `chat_sessions`에 비고유 복합 인덱스 `idx_chat_sessions_user_started_at` (`user_id` 오름차순, `started_at` 오름차순)을 추가한다. 리포트의 사용자·기간 조회를 지원하며 문서 내용, 조회 조건, 반환 필드는 바꾸지 않는다. 메시지 인덱스는 V15의 구성을 유지한다. 인덱스에는 저장 공간과 세션 쓰기 시 유지 비용이 추가된다.

MongoDB DDL과 MySQL의 Flyway 이력 기록은 원자적으로 커밋되지 않는다. V16은 같은 인덱스가 존재하는 상태에서 재실행할 수 있다. 애플리케이션 rollback 시에는 인덱스를 유지한다. 인덱스 제거가 필요하면 대상 DB의 인덱스 정의와 영향을 먼저 확인한 뒤 새 Java Flyway 보상 migration에서 해당 이름의 인덱스만 제거한다. 기존 V16과 Flyway 이력은 수정·삭제하지 않는다. 테스트 프로필은 Flyway 자동 실행을 끄므로 별도 통합 테스트에서 Spring이 찾은 Java migration을 H2 이력과 embedded MongoDB에 적용한다.

## 현재 제약

인증·인가·로그의 알려진 보완 사항이 남아 있으므로 기능 설명을 운영 준비 완료로 해석하지 않는다. 사용자 시간대 정책의 전체 기능 통일, 구독 이벤트 순서 처리, 외부 푸시의 전달 보장, 관리자 보정 runbook도 각각 별도 범위다. 점검의 상세 재현과 특정 실행 결과는 로컬 문서에 보관한다.
