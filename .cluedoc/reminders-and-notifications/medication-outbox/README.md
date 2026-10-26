---
title: 복약 예약의 영속 작업과 복구
sources:
  - src/main/java/com/forA/chatbot/medications/domain/MedicationScheduleOutbox.java
  - src/main/java/com/forA/chatbot/medications/domain/MedicationBundle.java
  - src/main/java/com/forA/chatbot/medications/repository/MedicationScheduleOutboxRepository.java
  - src/main/java/com/forA/chatbot/medications/service/MedicationScheduleOutboxWriter.java
  - src/main/java/com/forA/chatbot/medications/service/MedicationService.java
  - src/main/java/com/forA/chatbot/notification/service/MedicationOutboxDispatcher.java
  - src/main/java/com/forA/chatbot/notification/service/MedicationSchedulerService.java
  - src/main/java/com/forA/chatbot/notification/controller/InternalNotificationController.java
  - src/main/resources/db/migration/V13__Add_medication_schedule_outbox.sql
  - src/test/java/com/forA/chatbot/notification/service/MedicationOutboxIntegrationTest.java
  - src/faultTest/java/com/forA/chatbot/notification/fault
  - docs/development/fault-tests/outbox.md
  - docs/runbooks/medication-outbox.md
---

## Hero Visual

~~~mermaid
flowchart LR
    Change[복약 계획 변경] --> Commit[계획·예약 의도 함께 저장]
    Commit --> Signal[커밋 이후 깨우기]
    Signal --> Claim[등록 작업 선점]
    Recover[인증된 외부 복구 호출] --> Claim
    Claim --> Queue[버전별 예약 등록]
    Queue --> Guard[현재 계획·날짜·버전 확인]
    Guard --> Dedup[기기별 발송 선점]
    Dedup --> Push[푸시 전송]
~~~

## Abstract

복약 알림은 계획 저장, 외부 예약 등록, 기기별 발송을 서로 다른 결과로 기록한다. 예약 의도를 계획과 함께 보존하므로 저장 직후 중단되거나 외부 등록에 실패해도 작업을 다시 찾을 수 있다.

## Introduction

애플리케이션 메모리의 변경 이벤트는 즉시 처리를 깨우는 신호다. 이벤트를 받지 못해도 예약 의도는 관계 데이터에 남는다. 예정 시각의 발송은 계속 외부 작업 큐가 담당하며, 복구 호출은 미완료 등록 작업을 처리한다.

## Related Work

- [리마인더와 알림](../README.md) — 전체 전달 흐름과 중복 방지의 한계.
- [복약 루틴](../../medication-routines/README.md) — 계획 변경과 일별 실행 기록.
- [일기 알림 설정과 재예약](../diary-reminders/README.md) — 별도로 유지하는 기존 재예약 방식.
- [운영 신뢰성](../../operational-reliability/README.md) — 외부 복구 호출과 관측 연결.

## Description

계획의 생성·수정·삭제와 예약 의도는 같은 저장 단위에 속한다. 변경 버전은 동시 수정 검사에도 사용하고, 같은 계획·날짜·버전의 작업은 하나만 둔다. 일별 배치가 동시에 호출되어도 같은 의도로 수렴한다. 변경 시 오늘뿐 아니라 이미 예약 의도가 존재하는 미래 날짜도 새 버전으로 갱신한다.

같은 의도의 등록 재시도는 같은 외부 식별자를 사용하고, 다른 변경 버전은 다른 식별자를 사용한다. 기존 식별자를 삭제하고 다시 만들지 않는다. 이전 예약이 남아 호출되더라도 현재 계획과 버전이 다르거나 삭제된 계획이면 발송하지 않는다. 버전 없는 기존 요청은 아직 변경되지 않은 초기 버전에만 허용한다.

~~~mermaid
stateDiagram-v2
    [*] --> Pending: 유효한 미래 의도 저장
    Pending --> Processing: 원자적 선점
    RetryWaiting --> Processing: 재시도 시각 도달
    Processing --> Completed: 외부 등록 완료
    Processing --> RetryWaiting: 등록 실패·처리권 만료
    Processing --> Failed: 시도 소진
    Processing --> Superseded: 삭제·버전 변경
    Processing --> Expired: 예정 날짜 지남
    Failed --> Pending: 인증된 수동 재시도
~~~

처리권은 5분 뒤 만료되고, 회수된 처리권의 이전 소유자는 상태를 덮어쓸 수 없다. 외부 등록 중에는 저장 트랜잭션을 유지하지 않는다. 최초 시도를 포함해 최대 4번 등록하며 재시도 간격은 1분·5분·15분이다. 실패 종료한 작업도 보존하고 오류 원문 대신 제한된 오류 종류만 남긴다.

처음 설정할 때 이미 지난 시각은 새로 예약하지 않는다. 미래 의도로 저장된 뒤 등록이 지연됐다면 같은 서울 날짜 안에서는 늦게 등록하고, 날짜가 지난 작업은 만료한다. 실패 종료는 자동으로 무한 재시도하지 않으며 운영자가 상태를 확인하고 재시도를 요청한다.

예약 버전과 발송 중복 키는 다르다. 버전이 바뀌어도 같은 계획·날짜·기기에서 이미 발송한 알림은 다시 보내지 않는다. 예약 등록 완료는 기기 도달 완료가 아니다. 푸시 성공과 완료 기록 사이에 중단되면 중복 전달 가능성이 남고, 이미 진행 중인 외부 전송을 설정 변경으로 취소하는 보장도 없다.

기본 실행은 커밋 이후 즉시 시도와 인증된 외부 복구 호출이다. 서버 내부 주기 검색에 복구 실행을 의존하지 않으므로 운영 환경에서 외부 호출을 연결해야 한다. 현재 소스는 클라우드 리소스와 운영 연결을 자동 생성하지 않는다.

선택 실행하는 로컬 장애 실험은 실제 관계 저장소와 독립 프로세스를 사용한다. 저장 직후 종료, 등록 요청 실패와 응답 유실, 동시 선점, 처리 중 종료 후 회수, 이전 요청 차단을 확인한다. 외부 등록과 푸시 전달은 제어 가능한 로컬 대역으로 바꾸므로 실제 클라우드 인증·실행·기기 도달의 검증과 구분한다.

~~~mermaid
flowchart LR
    Fault[종료·통신 실패 주입] --> Processes[독립 처리 프로세스]
    Processes <--> Database[실제 관계 저장소]
    Processes --> Double[로컬 등록·푸시 대역]
    Database --> Evidence[상태·시간·호출 수 증거]
    Double --> Evidence
~~~

## Conclusion

저장된 예약 의도를 복구 기준으로 삼는다. 버전으로 이전 예약을 무효화하고, 원자적 선점과 제한된 재시도로 등록을 복구하며, 기존 발송 기록으로 논리적인 중복을 줄인다.
