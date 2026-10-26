---
title: 일기와 인사이트
sources:
  - src/main/java/com/forA/chatbot/diary
  - src/main/java/com/forA/chatbot/report
  - src/main/java/com/forA/chatbot/global/time
  - src/test/java/com/forA/chatbot/diary/service
  - src/test/java/com/forA/chatbot/report
  - src/faultTest/java/com/forA/chatbot/report/fault
  - src/main/java/com/forA/chatbot/config/migration/V16__Add_chat_session_report_index.java
---

## Hero Visual

~~~mermaid
flowchart LR
    Reflection[순간·생각·감정 회고] --> Draft[선택 날짜의 초안]
    Draft --> Complete[점수와 태그로 완료]
    Complete --> Daily[날짜별 조회]
    Complete --> Monthly[월간 감정 추세에 2배 반영]
    Chat[대화 감정] --> Weekly[주간 감정 추세]
    Medication[복약 컨디션] --> Weekly
    Chat --> Monthly
    Medication --> Monthly
    Complete --> Summary[최근 이틀 태그 요약]
~~~

## Abstract

일기와 인사이트는 짧은 회고를 저장하고 감정·복약 기록을 기간별 변화로 보여 준다. 일기는 오늘이나 과거 날짜의 초안을 완성하는 흐름이며, 리포트는 주간과 월간에 서로 다른 자료·빈 값 정책을 적용한다.

## Introduction

긴 글을 쓰기 어려운 사용자도 기억에 남는 순간, 떠오른 생각, 느낀 감정을 남길 수 있다. 초안에 공감 코멘트를 받은 뒤 감정 점수와 태그를 선택하면 같은 기록이 완료된다.

리포트는 실제 기록을 날짜별로 모으고 기간을 비교한다. 일기 작성 날짜, 기록을 수정한 시각, 리포트를 조회하는 날짜는 서로 다른 의미를 가진다.

## Related Work

- [Mori / Foryn 지원 서버](../README.md) — 전체 기능 지도.
- [선택 날짜의 한줄일기](./selected-date-diary/README.md) — 초안·완료·한도·만료 정리.
- [감정 추세와 요약](./emotion-insights/README.md) — 주간·월간 자료 차이와 요약 재사용.
- [계정과 인증](../identity-and-accounts/README.md) — 기록 소유권과 프로필.
- [지원형 대화](../supportive-conversations/README.md) — 감정 리포트의 대화 자료.
- [복약 루틴](../medication-routines/README.md) — 복약 리포트와 컨디션 자료.
- [일기 알림 설정과 재예약](../reminders-and-notifications/diary-reminders/README.md) — 작성 시각 알림.

## Description

일기는 사용자 현지의 오늘보다 미래인 날짜를 거부한다. 선택 날짜별로 초안과 완료 기록을 합쳐 최대 3개를 허용한다. 오늘의 기존 초안을 다시 생성하면 충돌로 반환하고, 과거 날짜의 기존 초안은 답변과 코멘트를 갱신한다. 초안 만료는 기록 대상 날짜가 아니라 마지막 수정 시각으로 판단한다.

~~~mermaid
stateDiagram-v2
    state "초안" as Draft
    state "완료 기록" as Completed
    state "삭제" as Expired
    [*] --> Draft: 오늘·과거 날짜 회고
    Draft --> Draft: 과거 날짜 초안 갱신
    Draft --> Completed: 감정 점수와 태그 확정
    Draft --> Expired: 현지 오늘 이전에 마지막 수정
    Completed --> [*]
~~~

일기 코멘트는 진단·평가·치료·복약 조언을 피하도록 생성 지침과 출력 검사를 적용한다. 외부 생성 실패나 부적합한 결과에는 대체 문구를 사용해 기록을 계속할 수 있게 한다.

감정 리포트의 공통 자료는 대화 감정과 복용 완료 기록의 컨디션이다. 월간에만 완료 일기의 감정 점수를 2배 가중치로 추가한다. 자료 없는 날은 주간에 0, 월간에 빈 점수로 표시하고, 자료 조회 장애는 별도 실패로 반환한다.

대화 세션의 기간 조회는 사용자와 시작일의 복합 색인으로 범위를 좁힌다. 색인은 저장소 변경 이력에 따라 준비하며, 리포트의 집계 기준은 유지한다.

월간 화면의 요약은 선택 월 전체가 아니라 사용자 현지의 오늘을 제외한 최근 이틀의 완료 일기 태그를 설명한다. 사용자와 현지 날짜별로 저장된 요약을 재사용하므로 조회 월이나 당일 기록이 바뀌어도 같은 날의 요약은 유지된다.

복약 리포트는 계획의 생성·삭제 시점을 고려하며 삭제된 계획도 삭제일까지 포함한다. 다만 변경 전 요일·항목을 모두 복원하는 이력 모델은 아니다. 주간 종합은 관찰 기간과 비교 자료가 충분할 때 감정 변화와 복약 실행률을 계산한다.

## Conclusion

주간 감정 조회의 성능 방어선은 실제 저장소를 사용하는 로컬 비교 실험으로 점검한다. 자료 조회가 늦어질 때 관계 저장소 연결을 계속 점유하는지와 일반 조회로 대기가 전파되는지를 구분해 기록한다.

일기 기록의 수명 주기와 리포트의 해석 정책을 구분해야 한다. 날짜별 작성 가능 수, 마지막 수정에 따른 만료, 월간에 한정된 일기 가중치, 조회일 기준 요약이 현재 동작의 핵심이다.
