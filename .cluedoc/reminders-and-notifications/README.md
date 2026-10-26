---
title: 리마인더와 알림
sources:
  - src/main/java/com/forA/chatbot/notification
  - src/main/java/com/forA/chatbot/medications/domain/MedicationScheduleOutbox.java
  - src/main/java/com/forA/chatbot/medications/service/MedicationScheduleOutboxWriter.java
  - src/main/java/com/forA/chatbot/diary/service/DiaryReminderService.java
  - src/main/java/com/forA/chatbot/diary/domain/DiaryReminderRescheduleJob.java
  - src/main/java/com/forA/chatbot/config/CloudTasksConfig.java
  - src/main/java/com/forA/chatbot/config/FirebaseConfig.java
  - src/main/java/com/forA/chatbot/config/InternalSecurityConfig.java
  - src/test/java/com/forA/chatbot/notification
  - src/faultTest/java/com/forA/chatbot/notification/fault
---

## Hero Visual

~~~mermaid
flowchart LR
    Plan[복약 계획·일기 설정 변경] --> Commit[저장 확정]
    Commit --> Intent[영속 예약 작업]
    Intent --> Queue[예약 등록·복구]
    Medication[복약 주기 검색] --> Claim[발송 처리권]
    Queue --> Guard[내부 호출 인증]
    Guard --> Claim
    Claim --> Push[푸시 전달]
    Device[활성 기기 설정] --> Push
    Push --> Log[발송 상태 기록]
~~~

## Abstract

리마인더와 알림은 기기와 수신 설정을 바탕으로 복약, 일기 작성, 대화 재참여, 연속 실천 메시지를 전달한다. 복약·일기 예약 전달은 처리권과 발송 기록으로 중복을 줄인다. 알림 종류마다 예약·재시도·중복 방지 범위가 다르다.

## Introduction

복약 알림에는 작업 큐 예약과 주기 검색 경로가 있고, 일기 알림은 작업 큐 예약을 사용한다. 일기 재예약 작업을 주기적으로 검색하는 복구 절차는 푸시를 직접 보내는 주기 검색과 다르다.

예약 실행은 사용자 요청과 별도의 인증 경계에서 발급자·대상·실행 계정을 확인한다. GCP Cloud Tasks를 통한 예약과 Firebase FCM을 통한 푸시는 기존 운영에서 설정·사용한 연동이다. 운영 중단 후 현재 복사본에서는 운영 연결과 자격증명을 제거했으며, 예약·인증·전달 구조는 보존한다. 실제 전달을 재개할 때에는 연결과 자격증명을 다시 주입하고 검증한다.

## Related Work

- [Mori / Foryn 지원 서버](../README.md) — 전체 기능 지도.
- [일기 알림 설정과 재예약](./diary-reminders/README.md) — 전체 교체 설정과 재예약 복구.
- [복약 예약의 영속 작업과 복구](./medication-outbox/README.md) — 계획과 함께 남기는 예약 의도와 버전별 등록.
- [계정과 인증](../identity-and-accounts/README.md) — 기기 소유권과 로그아웃 정리.
- [복약 루틴](../medication-routines/README.md) — 복약 일정의 원천.
- [일기와 인사이트](../journaling-and-insights/README.md) — 일기 알림의 목적지.
- [대화 저장의 확정과 복구](../supportive-conversations/reliable-storage/README.md) — 대화 알림 예약·취소가 실행되는 확정 시점.
- [운영 신뢰성](../operational-reliability/README.md) — 외부 연동과 관측의 검증 범위.

## Description

복약 계획 변경은 예약 의도와 함께 저장한다. 확정 이후 작업을 선점해 변경 버전별로 외부 예약을 등록하고, 이전 버전의 발송 요청은 무시한다. 일별 복약 배치도 외부에 바로 등록하지 않고 예약 의도를 남긴다. 일기 알림은 최대 3개 시각을 전체 교체하고 같은 저장 단위에 재예약 작업을 남기며, 확정 이후 기존 예약을 지우고 현재 활성 설정을 다시 예약한다. 일별 배치와 복약 예약 복구를 호출하는 운영 연결은 별도로 구성해야 한다.

~~~mermaid
sequenceDiagram
    participant Queue as 예약 호출
    participant Guard as 처리권과 발송 기록
    participant Push as 푸시 전달망
    Queue->>Guard: 종류·대상·날짜·기기로 선점
    alt 발송 완료 기록 있음
        Guard-->>Queue: 처리 완료
    else 다른 처리자가 점유
        Guard-->>Queue: 이후 재시도 필요
    else 처리권 획득
        Queue->>Push: 푸시 요청
        alt 전달 성공
            Queue->>Guard: 발송 완료 기록
        else 전달 실패
            Queue->>Guard: 처리권 해제
        end
    end
~~~

발송 기록은 알림 종류·대상·예정 날짜·기기 조합으로 구분한다. 활성 처리권은 경쟁 발송을 막고 만료된 처리권은 다시 획득할 수 있다. 푸시 성공 뒤 완료 기록 전에 중단되면 재전달될 수 있으므로 정확히 한 번 전달을 보장하지 않는다. 이 방식을 모든 대화·축하 알림에 공통 적용한 것으로 해석하면 안 된다.

전달망의 연결 오류는 한 번 즉시 재시도하고 무효한 기기 토큰은 정리한다. 복약 예약은 영속 작업과 처리권을 사용하며, 외부 복구 호출로 등록 재시도와 오래된 처리권 회수를 수행한다. 일기 재예약도 별도로 저장한 작업에 제한된 재시도와 장기 처리 복구를 적용하지만 실행 방식과 실패 종료 정책은 다르다. 모든 후속 알림에 이 구조가 공통 적용된 것은 아니다.

대화 재참여 알림은 사용자가 행동을 선택한 대화가 확정된 뒤 예약하고, 유효한 행동 확인 답변이 확정된 뒤 취소한다. 입력 검증·대화 저장이 실패하면 예약 상태를 먼저 바꾸지 않는다. 이 예약은 메모리에 유지하므로 재시작 뒤 복원과 백그라운드 대화 복구에서의 재생은 보장하지 않는다. 예약 동작의 오류로 확정 대화를 취소하지 않는다.

수신을 끈 기기는 발송 대상에서 제외하고 로그아웃하면 계정의 기기 토큰을 정리한다. 성공·실패·처리 수·지연은 관측 신호로 남긴다. 민감정보 최소 기록은 운영 요구사항이며 현재 복사본 전체에서 달성된 보장으로 간주하지 않는다.

복약 예약은 실제 관계 저장소와 독립 프로세스로 종료·통신 실패·동시 선점을 재현하는 선택 검사를 제공한다. 로컬 등록·푸시 대역의 호출 수와 저장 상태를 판정하며, 실제 외부 인증이나 기기 수신을 확인한 것으로 해석하지 않는다.

별도 로컬 검사는 내부 호출의 실제 서명·발급자·대상·실행 계정 판정과 외부 예약 요청의 조립·오류 전파를 확인한다. 서명 검증은 임시 공개키를 사용하고, 예약 SDK 호출은 대역으로 바꾸므로 실제 클라우드 권한·작업 실행의 검증과 구분한다.

## Conclusion

알림은 설정 저장, 예약, 내부 인증, 처리권 획득, 외부 전달, 완료 기록으로 이어진다. 사용자 의도 저장과 외부 전달을 분리하되, 재시도 가능 범위와 중복 전달의 한계를 함께 이해해야 한다.
