---
title: 복약 루틴
sources:
  - src/main/java/com/forA/chatbot/medications
  - src/main/java/com/forA/chatbot/report/service/ReportService.java
---

## Hero Visual

~~~mermaid
flowchart LR
    Plan[복약 계획] --> Schedule[요일과 시간]
    Schedule --> Today[오늘의 복약 목록]
    Today --> Record[복용 여부 기록]
    Record --> Streak[연속 실천 확인]
    Record --> Insight[주간·월간 인사이트]
    Plan --> Reminder[복약 리마인더]
~~~

## Abstract

복약 루틴은 사용자가 복용 계획을 만들고, 날짜별로 해야 할 일을 확인하며, 실제 복용 여부와 상태를 기록하게 한다. 계획 변경은 알림 예약에 반영되고, 기록은 중복 요청에도 한 번만 확정되며, 연속 실천과 기간별 리포트의 근거가 된다.

## Introduction

복약 경험은 계획과 실행을 분리해 관리한다. 계획에는 사용자가 알아볼 이름, 적용 요일, 복용 시각, 관련 항목이 들어가고, 일별 목록은 선택한 날짜에 해당하는 활성 계획만 시간순으로 보여 준다. 실행 기록은 그 계획을 실제로 수행했는지와 사용자가 남긴 상태를 날짜에 연결한다.

모든 계획과 기록은 인증된 계정의 소유권 아래에 있다. 다른 사용자의 계획을 조회하거나 변경할 수 없으며, 같은 이름의 활성 계획이나 같은 날짜의 중복 실행 기록이 생기지 않도록 애플리케이션 검증과 저장 제약을 함께 사용한다.

## Related Work

- [Mori / Foryn 지원 서버](../README.md) — 복약 루틴이 속한 전체 지원 경험.
- [계정과 인증](../identity-and-accounts/README.md) — 계획과 기록의 사용자 소유권 기준.
- [리마인더와 알림](../reminders-and-notifications/README.md) — 계획 변경 후 예약되고 전달되는 복약 알림.
- [복약 예약의 영속 작업과 복구](../reminders-and-notifications/medication-outbox/README.md) — 계획 변경과 예약 의도의 원자적 저장.
- [일기와 인사이트](../journaling-and-insights/README.md) — 복약 실행률과 정서 변화를 함께 보여 주는 리포트.

## Description

계획의 생성·수정·삭제는 하나의 수명 주기를 이룬다. 삭제는 과거 리포트의 사실을 보존할 수 있도록 비활성화로 처리된다. 계획 변경과 예약 의도를 함께 저장한 뒤 알림 등록 처리를 깨운다. 동시 수정은 변경 버전으로 충돌을 감지하고, 이전 버전의 예약은 발송 시점에 무시한다.

~~~mermaid
stateDiagram-v2
    state "활성 계획" as Active
    state "변경된 계획" as Updated
    state "비활성 계획" as Deleted

    [*] --> Active: 계획 생성
    Active --> Updated: 요일·시간·항목 변경
    Updated --> Updated: 추가 변경
    Updated --> Deleted: 삭제
    Active --> Deleted: 삭제
    Deleted --> [*]
~~~

일별 목록은 선택 날짜의 요일과 활성 계획의 적용 요일을 맞추고 그날의 실행 기록을 결합한다. 현재 복약 계획 시각은 서울 기준으로 입력·표시하고 저장 시각으로 변환하며, 일기의 사용자별 시간대 정책과 같지 않다.

실행 기록은 같은 계획과 날짜의 조합을 한 번만 허용한다. 동일 요청이 반복되면 실패를 새로 만들기보다 이미 확정된 결과를 반환하고, 동시에 도착한 요청도 저장 제약으로 한 기록에 수렴시킨다. 복용 완료를 선택한 경우에는 사용자가 요청된 상태 정보를 함께 제공했는지 확인한다.

기록이 저장되면 최근 3일의 예정된 계획을 모두 수행했는지 확인하고 축하 이벤트를 발행한다. 후속 확인이나 알림 실패가 저장된 복약 기록을 되돌리지는 않는다. 리포트는 계획의 생성·삭제일을 고려해 삭제된 계획도 삭제일까지 포함하지만, 변경 전 요일·항목을 별도 이력에서 복원하는 방식은 아니다.

## Conclusion

복약 루틴의 핵심은 계획, 날짜별 기대 행동, 실제 기록을 정확히 연결하는 것이다. 소유권 검증, 중복 기록의 멱등성, 과거 계획 보존, 커밋 이후 알림 연계가 사용자의 일상 기록을 안정적인 장기 데이터로 만든다.
