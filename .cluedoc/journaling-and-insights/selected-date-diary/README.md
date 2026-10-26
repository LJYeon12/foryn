---
title: 선택 날짜의 한줄일기
sources:
  - src/main/java/com/forA/chatbot/diary/controller/DiaryController.java
  - src/main/java/com/forA/chatbot/diary/service/DiaryService.java
  - src/main/java/com/forA/chatbot/diary/service/DiaryDraftCleanupService.java
  - src/main/java/com/forA/chatbot/diary/service/DiaryTransactionExecutor.java
  - src/main/java/com/forA/chatbot/diary/service/DiaryAiService.java
  - src/main/java/com/forA/chatbot/diary/repository/DiaryRepository.java
  - src/main/java/com/forA/chatbot/diary/domain/Diary.java
  - src/test/java/com/forA/chatbot/diary/service/DiaryServiceTest.java
  - src/test/java/com/forA/chatbot/diary/service/DiaryServiceTransactionTest.java
---

## Hero Visual

~~~mermaid
flowchart TB
    Date[오늘·과거 날짜 선택] --> Validate[세 질문 답변과 미래 여부 검증]
    Validate --> Cleanup[마지막 수정이 현지 오늘 이전인 초안 삭제]
    Cleanup --> Existing{선택 날짜에 초안 존재}
    Existing -->|오늘| Conflict[중복 생성 거부]
    Existing -->|과거| Update[같은 초안의 답변·코멘트 갱신]
    Existing -->|없음| Limit{선택 날짜 기록이 3개 미만}
    Limit -->|예| Create[새 초안 저장]
    Limit -->|아니오| Reject[한도 초과]
    Create --> Complete[점수·태그로 같은 일기 완료]
    Update --> Complete
~~~

## Abstract

선택 날짜의 한줄일기는 오늘이나 과거의 회고를 초안으로 저장하고 감정 점수와 태그로 완성한다. 기록할 날짜별 한도와 사용자 현지 날짜를 구분하고, 과거 초안을 다시 작성해도 동일한 일기 식별자를 유지한다.

## Introduction

기억을 뒤늦게 기록할 때는 작성하는 오늘과 기록 대상 날짜가 다를 수 있다. 서버는 날짜별로 남은 작성 수를 계산하면서도 오늘의 생성·완료 개수는 별도로 응답한다. 날짜 조회는 완료 목록과 작성 중인 초안을 함께 제공하며, 화면 배치는 앱이 결정한다.

## Related Work

- [일기와 인사이트](../README.md) — 기록에서 리포트까지의 전체 흐름.
- [감정 추세와 요약](../emotion-insights/README.md) — 완료 기록의 점수·태그를 사용하는 후속 기능.
- [계정과 인증](../../identity-and-accounts/README.md) — 일기 소유자 식별.
- [일기 알림 설정과 재예약](../../reminders-and-notifications/diary-reminders/README.md) — 작성 시각 안내.

## Description

순간·생각·감정의 세 질문에 중복 없이 답해야 한다. 각 답은 공백만으로 구성될 수 없고 최대 500자다. 신규 계정의 시간대는 서울이며 로그인·갱신에서 자동으로 변경하지 않는다. 저장된 시간대가 유효하지 않으면 서울을 사용한다. 생성·조회는 현지 오늘보다 미래인 날짜를 거부한다.

선택 날짜의 초안과 완료 기록을 합쳐 최대 3개까지 허용하고 초안은 한 날짜에 하나만 둔다. 오늘의 초안 재생성은 거부하지만, 과거의 기존 초안은 답변과 생성 코멘트를 바꿀 수 있다. 동시 요청에는 초안 고유 제약, 저장 직전 한도 재확인, 기존 초안 잠금을 사용한다.

~~~mermaid
flowchart LR
    Input[기록 날짜] --> Quota[그 날짜의 초안과 완료 합계]
    Quota --> Remaining[3개에서 뺀 남은 수]
    Clock[사용자 현지 오늘] --> Counts[오늘 생성·완료 개수]
    Clock --> Midnight[현지 오늘 자정]
    Modified[초안 마지막 수정 시각] --> Expiry{자정보다 이전인가}
    Midnight --> Expiry
    Expiry -->|예| Delete[해당 사용자 초안 삭제]
~~~

만료 정리는 생성·완료·조회 처리에 앞서 별도 저장 단위로 실행한다. 기록 대상이 과거라도 오늘 수정한 초안은 유지되고, 완료 요청이 이후 실패해도 이미 정리한 만료 초안은 복원하지 않는다. 완료 기록은 이 정리의 대상이 아니다.

공감 코멘트는 외부 생성 실패, 빈 결과, 출력 규칙 위반 시 대체 문구로 바꾼다. 기록 내용의 길이 등 관측 신호를 남기며, 생성 실패가 일기 저장 자체를 막지 않도록 한다.

완료는 소유자의 초안을 잠근 뒤 같은 일기에 감정 점수와 태그를 저장한다. 점수는 -2부터 2까지이며, 태그는 빈 목록을 허용하되 최대 6개이고 중복·미정의 값은 거부한다. 이미 완료한 기록을 다시 완료하려는 요청은 충돌로 반환한다. 과거 날짜의 완료 안내에는 오늘의 몇 번째 기록이라는 표현을 사용하지 않는다.

날짜 조회의 완료 목록은 완료 시각과 식별자 순서로 정렬하며, 선택 날짜의 초안도 함께 반환한다. 남은 수는 선택 날짜를 기준으로 계산하고 오늘의 개수와 구분한다.

## Conclusion

기록 날짜는 한도와 조회의 기준이고, 마지막 수정 시각은 초안 만료의 기준이다. 오늘과 과거 초안의 재작성 정책, 동일 기록의 완료 전환, 선택 날짜별 남은 수를 함께 이해하면 기록 화면과 서버의 역할을 구분할 수 있다.
