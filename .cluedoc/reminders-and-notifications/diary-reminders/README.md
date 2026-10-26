---
title: 일기 알림 설정과 재예약
sources:
  - src/main/java/com/forA/chatbot/diary/service/DiaryReminderService.java
  - src/main/java/com/forA/chatbot/diary/domain/DiaryReminderRescheduleJob.java
  - src/main/java/com/forA/chatbot/notification/service/DiaryReminderNotificationService.java
  - src/main/java/com/forA/chatbot/notification/scheduler/DiaryReminderRescheduleJobScheduler.java
  - src/main/java/com/forA/chatbot/notification/service/NotificationSendLogService.java
  - src/main/java/com/forA/chatbot/user/service/UserService.java
  - src/test/java/com/forA/chatbot/diary/service/DiaryReminderServiceTest.java
  - src/test/java/com/forA/chatbot/notification
---

## Hero Visual

~~~mermaid
flowchart TB
    User[최대 3개 알림 시각 전체 교체] --> Save[설정과 재예약 작업 함께 저장]
    Save --> Commit[저장 확정]
    Commit --> Claim[재예약 작업 선점]
    Poll[주기 검색·장기 처리 복구] --> Claim
    Claim --> Replace[기존 당일 예약 삭제·현재 설정 예약]
    Replace --> Success[작업 성공]
    Replace --> Failure[실패 기록]
    Failure --> Retry[1분·5분·15분 후 재시도]
    Retry --> Claim
~~~

## Abstract

일기 알림은 사용자가 고른 시각을 저장하고, 저장 이후 외부 예약을 교체한다. 외부 예약 실패로 사용자 설정을 되돌리는 대신 별도 작업 상태를 남겨 제한된 재시도와 중단 복구를 수행한다.

## Introduction

설정 저장 성공과 실제 푸시 예약 성공은 서로 다른 결과다. 두 작업 사이에서 서버가 중단될 수 있으므로 재예약 의도를 설정과 함께 보존한다. 사용자 프로필 조회·수정 응답에도 저장된 알림 설정을 포함한다.

## Related Work

- [리마인더와 알림](../README.md) — 전달 경계와 기기별 발송 기록.
- [선택 날짜의 한줄일기](../../journaling-and-insights/selected-date-diary/README.md) — 알림을 받고 이어서 작성하는 기록 기능.
- [계정과 인증](../../identity-and-accounts/README.md) — 프로필과 기기 소유권.
- [운영 신뢰성](../../operational-reliability/README.md) — 외부 예약 환경의 별도 구성과 검증.

## Description

알림 시각은 분 단위 24시간 형식이며 활성 여부가 함께 필요하다. 활성 여부와 무관하게 같은 시각을 중복 등록할 수 없고 최대 3개다. 빈 목록은 전체 해제로 처리한다. 사용자 시간대가 잘못되면 서울을 기준으로 한다.

기존 설정을 삭제하고 새 설정과 재예약 작업을 같은 저장 단위에 남긴다. 작업에는 이전 알림 식별자와 사용자 현지의 예정 날짜를 보관한다. 저장 확정 이후 비동기 처리자가 작업을 선점해 이전 예약을 삭제하고 현재 활성 설정을 읽어 예약한다. 이미 지난 시각은 예약하지 않는다.

~~~mermaid
stateDiagram-v2
    state "대기" as Pending
    state "처리 중" as Processing
    state "재시도 대기" as RetryWaiting
    state "성공" as Succeeded
    state "실패 종료" as Failed
    [*] --> Pending
    Pending --> Processing: 처리권 획득
    RetryWaiting --> Processing: 재시도 시각 도달
    Processing --> Succeeded: 예약 교체 성공
    Processing --> RetryWaiting: 재시도 여유 있음
    Processing --> RetryWaiting: 10분 이상 처리 중인 작업 복구
    Processing --> Failed: 재시도 소진·재시도 예약 실패
~~~

예약 교체 실패에는 최초 시도 이후 최대 3회의 재시도를 두며 간격은 1분·5분·15분이다. 기본 1분 주기 검색은 실행 가능한 작업을 찾고, 10분 이상 처리 중인 작업을 복구한다. 재시도 작업 자체를 외부에 예약하지 못하면 실패 종료로 남기고 오류 추적으로 알린다. 실패 종료를 무조건 다시 실행하는 구조는 아니다.

일별 배치는 활성 알림마다 해당 사용자의 날짜·시각을 외부 예약 시각으로 바꾼다. 같은 알림과 날짜의 예약이 이미 있으면 중복 성공으로 처리한다. 실제 운영에서는 이 배치를 호출할 예약 연결을 별도로 구성해야 한다.

발송 시점에는 설정이 여전히 존재하고 활성인지 확인한다. 수신을 허용한 기기마다 처리권을 얻어 발송하고, 이미 발송한 기기는 건너뛴다. 경쟁 처리 또는 전달 실패는 예약 호출의 재시도를 유도한다. 푸시 성공과 완료 기록 사이의 중단까지 없애는 방식은 아니므로 중복 전달 가능성은 남는다.

## Conclusion

저장된 설정, 재예약 작업의 완료, 기기별 발송 완료는 별개의 상태다. 확정 이후 실행과 영속 작업, 제한된 재시도, 오래된 처리 복구가 사용자 설정을 보존하면서 외부 예약 실패를 다루는 핵심이다.
