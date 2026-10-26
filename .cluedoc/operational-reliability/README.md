---
title: 운영 신뢰성
sources:
  - src/main/java/com/forA/chatbot/config
  - src/main/java/com/forA/chatbot/global
  - .github/workflows/pr-test-gate.yml
  - build.gradle
  - src/faultTest/java/com/forA/chatbot/notification/fault
  - src/faultTest/java/com/forA/chatbot/report/fault
  - src/faultTest/java/com/forA/chatbot/chat/fault
  - docs/runbooks/chat-recovery.md
  - docs/development/testing.md
  - docs/development/fault-tests/chat.md
  - docs/development/fault-tests/report.md
  - docs/development/fault-tests/outbox.md
  - docs/README.md
  - docs/ARCHITECTURE.md
  - src/integrationTest/java/com/forA/chatbot/ChatSessionIndexMigrationIntegrationTest.java
  - compose.yaml
  - infra/observability
  - infra/k6
  - scripts/tdd-guard
  - .githooks/pre-commit
---

## Hero Visual

~~~mermaid
flowchart LR
    Source[개발 소스] --> Verify[테스트와 구조 검증]
    Source --> Secrets[환경별 비밀 분리]
    Verify --> Artifact[비밀 파일을 제외한 빌드 결과]
    Secrets --> Artifact
    Artifact --> Setup[기존 구조에 따른 운영 연결 복원·검증]
~~~

## Abstract

운영 신뢰성은 개발 변경을 검증하고 민감한 자격증명을 소스와 배포물에서 분리하는 경계다. 관측과 장애 재현을 통해 저장·예약·전달의 실패 지점을 확인한다.

## Introduction

저장 완료, 외부 예약 등록과 기기 수신은 각각 다른 결과다. 검증은 이 경계를 나누어 확인하며, 로컬 대역의 성공과 실제 외부 서비스의 연동 성공을 구분한다.

## Related Work

- [Mori / Foryn 지원 서버](../README.md) — 공유본이 보존하는 전체 기능.
- [로컬 관측과 부하 확인](./local-observability/README.md) — 개발용 저장소, 서버 지표와 수동 부하 결과를 함께 확인하는 구성.
- [리포트 지연과 연결 점유 비교](./local-observability/report-pool-experiments/README.md) — 조회 지연·트랜잭션 범위·반복 조회의 영향을 분리하는 실험.
- [지원형 대화](../supportive-conversations/README.md) — 민감한 사용자 맥락을 다루는 외부 응답 경계.
- [대화 저장의 확정과 복구](../supportive-conversations/reliable-storage/README.md) — 부분 저장·응답 유실·프로세스 종료를 판정하는 영속 작업.
- [감정 추세와 요약](../journaling-and-insights/emotion-insights/README.md) — 기간 세션 조회의 색인과 감정 집계.
- [리마인더와 알림](../reminders-and-notifications/README.md) — 기존 작업 큐와 푸시 연동 구조를 보존하는 전달 기능.
- [복약 예약의 영속 작업과 복구](../reminders-and-notifications/medication-outbox/README.md) — 외부 주기 호출로 되살리는 등록 작업과 실패 종료.
- [구독 권한](../subscription-access/README.md) — 외부 결제 검증과 서버 권한 경계.

## Description

변경 관문은 테스트, 구조 검사, 정적 검사를 조합한다. 새 기능·수정은 실패 테스트를 먼저 확인하는 절차를 따른다. 커밋 전 로컬 관문은 정적 검사·빌드·테스트를 실행하지만, 보존된 PR 자동 검증 정의는 일반 테스트만 실행한다. 구조 검사와 통합 검사는 별도 호출 대상이며, 일반 빌드 성공이 모든 외부 연결의 검증을 의미하지 않는다.

~~~mermaid
flowchart TB
    Change[개발 변경] --> Red[실패 테스트 확인]
    Red --> Tests[구현 후 자동 테스트]
    Change --> Structure[계층과 의존 방향 검사]
    Change --> Static[정적 규칙 검사]
    Tests --> Review[검토 가능한 결과]
    Structure --> Review
    Static --> Review
    Review --> Future[운영 재개 환경의 별도 연동 검증]
~~~

로컬 자격증명은 Git 업로드에서 제외하고 빌드 리소스 복사에서도 보호한다. 공개 신뢰 인증서는 신원 검증에 필요하므로 개인키와 구분한다. 기존 조직으로 소스 컨텍스트를 업로드하던 빌드 연결은 제거했으며, 런타임 오류 추적과 외부 서비스 연동 코드는 개발 기능으로 보존한다.

문서 저장소의 색인도 관계 저장소의 변경 이력과 함께 관리한다. 리포트용 사용자·시작일 색인은 문서와 조회 결과를 유지하며 반복 준비가 가능하다. 두 저장소의 변경은 하나의 원자적 작업이 아니므로 이력 기록 전에 색인이 생성된 경우도 재실행할 수 있어야 한다. 애플리케이션을 이전 버전으로 되돌릴 때는 색인을 유지하며, 제거가 필요하면 별도의 보상 변경으로 처리한다.

운영 연결을 재개할 때에는 비밀 주입, 단계적 배포, 지표, 장애 대응과 rollback을 대상 환경에서 검증해야 한다. 이메일만으로 자격을 발급하던 임시 로그인은 제거했고 대화 응답의 소유권을 확인한다. Apple 신원 증거의 발급자·대상 검증과 다른 기능의 민감정보 로그는 알려진 미해결 항목이다.

공유 문서에는 기능 계약과 설계 근거를 남기고, 특정 작업의 실행 계획·업로드 준비·검증 수치는 로컬 전용 기록으로 분리한다. 공유 설명과 소스 참조는 로컬 문서 없이도 읽고 확인할 수 있어야 한다. 과거 테스트 성공과 현재 환경의 실행·외부 연동 검증은 구분한다.

현재 로컬 구성은 MySQL·MongoDB·Prometheus·Grafana 컨테이너와 호스트에서 실행하는 서버를 연결한다. k6는 수동으로 실행해 로컬 조회의 결과를 같은 지표 저장소로 보낸다. 데이터 볼륨을 보존하고 공개 포트를 로컬에 제한하며, 구성·부하 옵션 검증과 실제 기동 검증을 구분한다.

복약 예약의 장애 실험은 기존 데이터와 분리한 관계 저장소에서 별도로 실행한다. 독립 프로세스를 실제 종료하고 재시도 시각과 처리권 만료를 관측한다. 외부 등록·푸시는 로컬 대역이며 실행별 과정과 판정 결과를 남긴다. 일반 빌드 성공이나 이 로컬 실험의 성공으로 운영 연결의 성공을 대신하지 않는다.

대화 저장의 별도 검사는 관계 저장소와 문서 복제 구성을 함께 사용한다. 저장 거부, 실제 커밋 뒤 응답 유실, 보상 오류와 작업자 경쟁을 분리한다. 별도 실행자를 강제 종료하고 새 프로세스가 영속 기록으로 확정·보상을 이어가는지 검사한다. 일반 조회에서 숨긴 임시 자료도 원본 저장 상태로 확인하며, 조건 주입 결과를 운영 오류율로 해석하지 않는다.

내부 예약 인증은 임시 키와 실제 검증기로 거절 경계를 검사하고, 외부 등록 어댑터는 SDK 대역으로 요청·오류 처리를 검사한다. 실제 클라우드 인증·큐 실행·기기 수신의 확인과 로컬 정책 검증을 구분한다.

## Conclusion

리포트 장애의 통제 실험은 실제 관계·문서 저장소와 동일한 연결 풀을 사용한다. 느린 자료 조회 중의 연결 소유권과 다른 조회의 실패를 함께 확인하며, 과거 동작의 재구성과 현재 서비스 실행을 구분한다. 상세 결과는 개인 실행 기록으로 보관하고 반복할 수 있는 절차를 공유한다.

연결 획득 실패의 추가 진단에는 JDBC 구간별 시간, JVM 대기 기록과 일회성 DB 서버 처리 시간을 사용한다. 실패 시점과 실제 지연 구간을 연결할 수 있을 때 원인을 판정한다.

테스트, 관측과 장애 실험의 결과는 각 검사가 실제로 확인한 경계 안에서 해석한다. 외부 연동과 운영 복구는 재개 대상 환경에서 별도로 검증한다.
