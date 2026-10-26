---
title: 감정 추세와 요약
sources:
  - src/main/java/com/forA/chatbot/report/service/ReportService.java
  - src/main/java/com/forA/chatbot/report/service/EmotionScoreCalculator.java
  - src/main/java/com/forA/chatbot/report/service/DiaryEmotionScoreReader.java
  - src/main/java/com/forA/chatbot/diary/service/DiaryTagSummaryReader.java
  - src/main/java/com/forA/chatbot/diary/service/DiaryAiService.java
  - src/main/java/com/forA/chatbot/diary/service/MonthlySummaryRetryExecutor.java
  - src/main/java/com/forA/chatbot/diary/service/MonthlySummaryOutputValidator.java
  - src/main/java/com/forA/chatbot/diary/domain/DiaryReportSummaryCache.java
  - src/test/java/com/forA/chatbot/report
  - src/faultTest/java/com/forA/chatbot/report/fault
  - src/main/java/com/forA/chatbot/config/migration/V16__Add_chat_session_report_index.java
  - src/integrationTest/java/com/forA/chatbot/ChatSessionIndexMigrationIntegrationTest.java
  - docs/development/fault-tests/report.md
---

## Hero Visual

~~~mermaid
flowchart TB
    SessionLookup[사용자·시작일 색인으로 기간 세션 조회] --> Chat
    Chat[대화 감정] --> Common[공통 날짜별 자료]
    Medication[복용 완료 컨디션: 하루 최대 3건] --> Common
    Common --> Weekly[주간: 최근 7일과 이전 7일]
    Common --> Monthly[월간: 선택 월]
    Diary[완료 일기 점수: 가중치 2] --> Monthly
    Tags[현지 오늘 제외 최근 이틀의 일기 태그] --> Cache[사용자·조회일 기준 요약]
    Monthly --> Screen[월간 화면]
    Cache --> Screen
~~~

## Abstract

감정 추세는 여러 기록의 점수를 날짜별로 합치고, 월간 요약은 최근 완료 일기의 태그 관계를 짧게 설명한다. 주간·월간 그래프와 월간 화면의 요약은 자료 범위와 빈 값 처리 방식이 다르다.

## Introduction

월간 화면에 함께 표시된 그래프와 요약이 같은 기간을 분석한다고 가정하기 쉽다. 현재 그래프는 선택 월의 점수를 보여 주고, 요약은 조회 시점의 최근 이틀을 설명한다. 주간 리포트는 완료 일기의 점수를 사용하지 않는다.

## Related Work

- [일기와 인사이트](../README.md) — 기록과 리포트의 상위 기능.
- [선택 날짜의 한줄일기](../selected-date-diary/README.md) — 점수와 태그가 확정되는 시점.
- [지원형 대화](../../supportive-conversations/README.md) — 대화 감정 자료의 원천.
- [대화 저장의 확정과 복구](../../supportive-conversations/reliable-storage/README.md) — 집계에 포함할 확정 메시지의 기준.
- [복약 루틴](../../medication-routines/README.md) — 복용 컨디션 자료의 원천.
- [운영 신뢰성](../../operational-reliability/README.md) — 자료 조회 장애와 구간별 관측.
- [리포트 지연과 연결 점유 비교](../../operational-reliability/local-observability/report-pool-experiments/README.md) — 실제 조회 횟수와 지연 중 다른 조회의 보호 여부를 확인하는 실험.

## Description

대화 감정은 세션 시작 날짜에 연결하고 긍정·중립·부정을 각각 1·0·-1로 바꾼다. 복용을 완료한 기록의 컨디션은 날짜별 최대 3건까지 합친다. 월간은 여기에 완료 일기의 -2부터 2까지 점수를 각각 두 번 반영한 뒤 전체 점수의 평균을 낸다. 주간 그래프와 주간 변화율에는 일기를 추가하지 않는다.

| 구분 | 감정 자료 | 자료 없는 날짜 | 날짜 기준 |
| --- | --- | --- | --- |
| 주간 그래프 | 대화·복용 컨디션 | 0 | 서버의 오늘 기준 최근 14일을 두 구간으로 표시 |
| 월간 그래프 | 대화·복용 컨디션·완료 일기 2배 | 빈 점수 | 사용자 현지 오늘 기준 선택 월 |
| 주간 변화율 | 대화·복용 컨디션 | 비교 근거 부족 시 계산 불가 | 지난주와 그 이전 주 |

자료 조회 실패는 기록이 없는 상태와 구분한다. 필요한 자료를 읽지 못하면 리포트를 실패로 반환하고 구간별 실패 신호를 남긴다. 대화 메시지는 세션들을 묶어서 조회하며, 긴 목록은 나누어 조회한다.

기간 내 세션 조회에는 사용자와 시작일 순서의 복합 색인을 준비한다. 사용자 조건으로 범위를 좁힌 뒤 시작일 구간을 찾으므로 다른 사용자의 세션과 조회 기간 밖의 세션을 모두 검사하는 일을 줄인다. 같은 사용자와 시작일을 가진 여러 세션도 허용한다. 감정 계산과 반환 자료의 구성은 그대로 유지한다.

확정 전의 임시 대화 메시지는 감정 집계에서 제외한다. 새 확정 상태가 없는 과거 메시지도 정상 자료로 읽는다. 이 구분은 저장 실패로 남은 임시 응답이 리포트에 먼저 반영되는 일을 막는다.

~~~mermaid
flowchart LR
    Request[월간 화면 조회] --> Existing{사용자와 현지 오늘의 요약 존재}
    Existing -->|예| Reuse[기존 요약 재사용]
    Existing -->|아니오| Tags[그제·어제 완료 일기의 태그 수집]
    Tags --> HasData{최근 일기 존재}
    HasData -->|없음| NoData[기록 없음 안내]
    HasData -->|있음| HasTags{태그 존재}
    HasTags -->|없음| Fallback[기본 요약]
    HasTags -->|있음| Generate[생성·출력 검증]
    Generate --> Save[당일 요약 저장]
    NoData --> Save
    Fallback --> Save
~~~

요약은 선택 월을 저장 키에 포함하지 않는다. 같은 날 다른 월을 조회하거나 나중에 기록을 추가해도 이미 저장한 요약을 재사용한다. 최근 일기 없음과 태그 없음은 다른 안내로 처리한다. 생성 예외에는 최대 3회 시도와 짧은 지수 대기를 적용한다. 빈 결과와 출력 검사 실패에는 기본 요약을 사용하고, 이 결과도 당일 요약으로 저장한다.

주간 변화율은 충분한 가입 기간과 두 주의 관측값이 있을 때만 계산한다. 현재 주 평균은 0보다 큰 일별 점수만 선택하므로 모든 점수를 평균한 값과 구분해야 한다. 지난주 복약 실행률 역시 필요한 기간과 유효한 예정 횟수가 있어야 한다. 비교 불가는 건강 상태의 판단이 아니라 계산 근거가 부족하다는 뜻이다.

## Conclusion

기간·배치 조회와 짧은 관계 저장소 연결 점유는 별도 통제 실험으로 확인할 수 있다. 실험은 같은 주간 응답을 유지하면서 처리 방식별 조회 횟수와 연결 소유권을 비교한다. 월간 요약 생성이나 운영 화면 전체의 성능을 대신 검증하지는 않는다.

주간과 월간의 자료 선택, 일기의 2배 가중치, 빈 날짜의 표시 정책을 구분해야 수치를 올바르게 읽을 수 있다. 월간 요약은 선택 월 통계와 독립적인 조회일 기준 최근 기록 안내다.
