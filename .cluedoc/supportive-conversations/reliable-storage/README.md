---
title: 대화 저장의 확정과 복구
sources:
  - docs/development/fault-tests/chat.md
  - src/main/java/com/forA/chatbot/chat/domain
  - src/main/java/com/forA/chatbot/chat/repository
  - src/main/java/com/forA/chatbot/chat/service
  - src/main/java/com/forA/chatbot/config/ChatTransactionConfiguration.java
  - src/main/java/com/forA/chatbot/config/migration/V15__Prepare_chat_recovery.java
  - src/main/resources/db/migration/V14__Add_chat_recovery_journal.sql
  - src/faultTest/java/com/forA/chatbot/chat/fault
  - docs/runbooks/chat-recovery.md
---

## Hero Visual

~~~mermaid
flowchart TD
    Turn[사용자 입력과 응답 준비] --> Profile{프로필 변경?}
    Profile -->|없음| Commit[메시지·대화 단계 함께 확정]
    Profile -->|있음| Pending[대화 임시 저장]
    Pending --> SQL[프로필과 성공 근거 함께 저장]
    SQL -->|커밋 확인| Publish[임시 대화 확정]
    SQL -->|롤백 확인| Cancel[해당 임시 자료 정리]
    SQL -->|결과 불명| Recovery[기록을 보존하고 다시 판정]
    Publish --> Finish[완료 기록과 작업권 해제]
    Commit --> Finish
    Cancel --> Release[취소 기록과 작업권 해제]
~~~

## Abstract

대화 저장은 사용자의 메시지, 봇 응답, 다음 단계가 일부만 남지 않게 한다. 프로필과 대화를 서로 다른 저장소에 기록하는 요청은 진행 근거를 보존하고, 실패 후 확정 또는 정리를 이어간다. 대화 원문은 문서 저장소에 두고 관계 저장소에는 복구에 필요한 메타데이터만 남긴다.

## Introduction

사용자가 답변하면 대화 단계뿐 아니라 개인화 프로필도 바뀔 수 있다. 한 저장소의 성공이 다른 저장소의 성공을 뜻하지 않으며, 요청 중 연결이 끊겼다는 사실만으로 저장 실패를 단정할 수도 없다. 이 기능은 처리 중 자료와 확정 이력을 구분하고 재시작 후 판단할 근거를 제공한다.

## Related Work

- [지원형 대화](../README.md) — 입력과 단계 전이가 만드는 사용자 경험.
- [계정과 인증](../../identity-and-accounts/README.md) — 프로필과 초기화·삭제의 사용자 수명 주기.
- [감정 인사이트](../../journaling-and-insights/emotion-insights/README.md) — 확정된 대화만 읽는 집계 기능.
- [리마인더와 알림](../../reminders-and-notifications/README.md) — 대화 확정 뒤에 예약·취소하는 부수 효과.
- [운영 신뢰성](../../operational-reliability/README.md) — 일회성 저장소와 독립 프로세스의 장애 검사.

## Description

한 사용자에게 진행 중인 대화 변경은 하나만 허용한다. 입력 검증과 응답 준비는 저장 트랜잭션 밖에서 수행하며, 실제 사용자 엔티티 대신 분리된 작업 사본을 변경한다. 프로필 저장 시점에는 최신 값을 다시 확인해 준비 중 발생한 변경을 덮어쓰지 않는다.

프로필 변경이 없는 대화는 메시지 두 개와 다음 단계를 같은 문서 트랜잭션으로 확정한다. 프로필 변경이 있으면 임시 메시지와 다음 상태를 먼저 보존하고, 프로필과 성공 근거를 같은 관계 트랜잭션으로 커밋한 뒤 공개한다. 임시 상태는 일반 이력과 감정 집계에서 제외된다. 이전 버전의 정상 메시지는 새 상태 필드가 없어도 읽을 수 있다.

~~~mermaid
stateDiagram-v2
    state "응답 준비" as Preparing
    state "결과 확인·복구" as Recovering
    state "재시도 대기" as Retry
    state "확정 완료" as Completed
    state "정리 완료" as Cancelled
    state "수동 점검" as Manual
    Preparing --> Completed: 정상 저장
    Preparing --> Recovering: 실패 또는 작업권 만료
    Recovering --> Completed: 저장 성공 확인·확정 재시도
    Recovering --> Cancelled: 롤백 확인·임시 자료 정리
    Recovering --> Retry: 저장소 오류
    Retry --> Recovering: 다음 실행
    Retry --> Manual: 시도 소진
~~~

복구는 단순한 과거 값 복원이 아니다. 프로필 커밋이 확인되면 대화 확정을 이어가고, 롤백이 확인되면 해당 작업의 임시 자료만 정리한다. 두 저장소의 작업권 번호를 전진시키므로 늦게 돌아온 이전 실행자는 쓰기를 완료할 수 없다. 새 대화 생성, 상태 복구, 삭제도 같은 규칙에 참여한다. 삭제 후에도 작업권 근거는 남아 지연된 생성이 세션을 되살리지 못하게 한다.

알림 예약·취소는 저장 확정 뒤에 실행한다. 알림 실패로 확정 대화를 되돌리지 않는다. 기존 예약은 메모리에 있으므로 재시작 후 전달까지 보장하지 않는다. 작업을 반복하는 복구의 중복 방지와 클라이언트가 같은 입력을 새 요청으로 전송하는 경우의 식별도 별개다.

실행 중인 서버는 미완료 기록을 주기적으로 확인하고 제한된 재시도를 수행한다. 결과를 판정할 수 없거나 시도가 소진되면 작업권을 유지한다. 현재 활성 운영 연결과 외부 복구 호출은 없으며, 서버가 실행되지 않는 동안 정해진 복구 시간을 보장하지 않는다.

## Conclusion

로컬 장애 검사의 실행별 원본은 `.local/chat/experiments/fault-evidence/`에 저장한다. 과거 실행의 소스 지문은 당시 결과의 근거로 유지한다.

대화의 내부 원자성은 문서 트랜잭션으로 지키고, 프로필과 대화 사이의 부분 실패는 영속 기록에 근거한 보상·복구로 처리한다. 핵심은 실패를 숨기는 것이 아니라 미확정 상태를 식별하고, 성공과 취소를 안전하게 판정하며, 같은 작업을 반복해도 결과가 중복되지 않게 하는 데 있다.
