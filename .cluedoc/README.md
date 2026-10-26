---
title: Mori / Foryn 지원 서버
sources:
  - src/main/java/com/forA/chatbot
  - src/main/resources/db/migration
  - .github/workflows
  - docs/PRD.md
  - docs/ARCHITECTURE.md
  - docs/ADR.md
  - docs/README.md
  - compose.yaml
  - docs/development/fault-tests/report.md
---

## Hero Visual

~~~mermaid
flowchart LR
    User[모바일 사용자] --> Server[지원 API 구현]
    Server --> Identity[계정과 인증]
    Server --> Chat[지원형 대화]
    Server --> Journal[일기와 인사이트]
    Server --> Medication[복약 루틴]
    Server --> Notify[리마인더와 알림]
    Server --> Subscription[구독 권한]
    Operator[1인 운영자] --> Reliability[운영 신뢰성]
    Reliability --> Server
~~~

## Abstract

이 서버는 ADHD·정서·복약 지원 모바일 서비스의 사용자 계정, 대화, 기록, 루틴, 알림, 구독 상태를 하나의 백엔드에서 제공한다. 민감한 건강·정서 데이터와 결제·인증 상태를 다루므로 신원 검증, 중복 방지, 재시도 가능한 실패, 관측 가능한 운영이 중요한 요구사항이다.

## Introduction

사용자는 로그인한 뒤 자신의 상태를 돌아보는 대화를 나누고, 한줄일기와 복약 기록을 남기며, 필요한 시간에 알림을 받고, 기간별 변화를 확인한다. 서버는 이 경험을 하나의 흐름으로 연결하되 각 기능의 책임과 신뢰 원천을 분리한다.

사용자·권한·복약·구독처럼 관계와 일관성이 중요한 정보와 대화 세션·메시지는 서로 다른 저장 성격으로 관리된다. 외부 신원 제공자, 생성형 응답 제공자, 푸시 전달망, 작업 예약망과의 통신은 서버가 조정한다. 이메일 기반 임시 로그인은 제거했고 대화 응답의 세션 소유권을 확인한다. Apple 신원 증거의 발급자·대상 검증 누락과 민감정보 로그는 남아 있다. 기능 설명은 해당 보안 경계가 모두 완성되었다는 뜻이 아니다.

## Related Work

- [계정과 인증](./identity-and-accounts/README.md) — 사용자를 확인하고 보호된 기능에 접근시키는 신뢰 경계.
- [지원형 대화](./supportive-conversations/README.md) — 개인화된 단계형 정서 지원 경험.
- [일기와 인사이트](./journaling-and-insights/README.md) — 짧은 회고를 축적하고 기간별 변화로 보여 주는 기능.
- [복약 루틴](./medication-routines/README.md) — 복약 계획과 실행 기록을 일상 루틴으로 관리하는 기능.
- [리마인더와 알림](./reminders-and-notifications/README.md) — 약속된 시점의 행동을 안전하게 전달하는 비동기 기능.
- [구독 권한](./subscription-access/README.md) — 외부 결제 근거를 서버 권한 상태로 바꾸는 기능.
- [운영 신뢰성](./operational-reliability/README.md) — 배포, 관측, 실패 격리와 복구를 지원하는 운영 기능.

## Description

개인 기능 요청은 접근 자격으로 사용자를 식별한 뒤 기능별 정책을 실행한다. 소유권 검증의 적용 범위는 기능마다 다르다. 복약 계획 변경과 일기 알림 설정 변경은 저장이 확정된 뒤 예약 작업을 진행한다.

~~~mermaid
flowchart TB
    Request[사용자 요청] --> Trust{접근 자격 확인}
    Trust -->|통과| Capability[기능별 검증과 정책 실행]
    Trust -->|실패| SafeFailure[요청 거부]
    Capability --> Durable[권위 있는 상태 저장]
    Durable --> Response[사용자 응답]
    Durable --> Async[커밋 이후 후속 작업]
    Async --> Delivery[알림·외부 전달]
    Capability --> Signals[지연·오류·사용량 신호]
    Delivery --> Signals
    Signals --> Operator[운영 판단과 복구]
~~~

정식 로그인과 구독 검증은 외부 증거와 서버 저장 상태를 연결한다. 구독 상태를 저장하는 기능과 개별 기능에서 유료 권한을 강제하는 기능은 별개이며, 현재 대화의 구독 검사는 비활성화되어 있다. 대화와 일기의 생성 지침은 진단·처방·복용 변경 지시를 금지한다. 복약 기록과 예약 알림은 중복 요청을 식별하는 기록을 남긴다.

로컬 개발에서는 MySQL·MongoDB와 Prometheus·Grafana를 컨테이너로 실행하고, 호스트 서버의 지표와 수동 k6 실행 결과를 함께 확인할 수 있다. 이 개발 구성을 추가해도 중단된 과거 운영 연결은 자동으로 재개되지 않는다.

대화는 메시지와 다음 단계를 함께 확정하며, 프로필 변경을 포함하면 임시 저장과 영속 처리 기록으로 부분 실패를 복구한다. 복구 작업의 반복과 지연된 실행자는 사용자별 작업권으로 제어한다. 기존 클라이언트 요청 형식과 단계 경험은 유지한다.

리포트의 조회 지연과 연결 점유는 일회성 저장소를 사용하는 별도 실험으로 비교한다. 처리 방식에 따른 실제 조회 횟수, 연결 대기와 다른 조회의 응답을 기록하며 운영 성능 수치와 구분한다.

선택 진단에서는 JDBC 호출·JVM 대기·DB 서버 처리 기록을 함께 수집해 남은 연결 대기 구간을 좁힌다.

일기는 선택 날짜의 초안·완료 흐름을 제공하고, 리포트는 주간과 월간에 서로 다른 감정 자료를 사용한다. 일기 코멘트 생성 실패에는 대체 문구가 있으며, 일기 알림 재예약에는 별도로 저장한 작업과 재시도가 있다. 복약 예약도 계획과 함께 의도를 보존하고 변경 버전과 외부 복구 호출로 등록을 재처리한다. 외부 자료 조회 장애는 빈 리포트로 숨기지 않고 실패로 반환한다. 지표와 오류 추적 코드는 보존되어 있으나 실제 관측·배포·복구 환경은 새로 연결하고 검증해야 한다.

## Conclusion

각 기능은 사용자의 대화·기록·루틴을 연결하며 신원, 결제, 민감 데이터와 비동기 전달의 신뢰 경계를 가진다. 위 기능별 문서에서 현재 동작과 제약을 확인할 수 있다.
