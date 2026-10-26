---
title: 로컬 관측과 부하 확인
sources:
  - compose.yaml
  - .env.example
  - infra/local/application-observability.properties
  - infra/observability
  - infra/k6
  - infra/mongo
  - scripts/test_local_stack.py
  - scripts/test_k6_options.mjs
  - docs/development/local-setup.md
  - docs/development/observability.md
  - docs/development/testing.md
  - docs/development/fault-tests/report.md
  - scripts/report_fault.py
  - src/faultTest/java/com/forA/chatbot/report/fault
---

## Hero Visual

~~~mermaid
flowchart LR
    Developer[개발자] --> App[호스트 API 서버]
    App --> SQL[관계형 저장소]
    App --> Documents[대화 메시지 저장소]
    Collector[지표 수집기] -->|서버 지표 수집| App
    Load[수동 부하 실행] -->|로컬 조회| App
    Load -->|측정 결과 전송| Collector
    Dashboard[대시보드] -->|서버와 부하 지표 조회| Collector
~~~

## Abstract

로컬 관측과 부하 확인은 개발자가 실행한 서버의 응답과 자원 사용을 같은 시간축에서 비교하는 기능이다. 저장소와 관측 도구는 컨테이너로 실행하고, 서버는 호스트에서 실행하며, 부하는 개발자가 명시적으로 시작한다.

## Introduction

이 기능은 개발 환경에 관계형 저장소, 메시지 저장소, 지표 수집기와 대시보드를 제공한다. 서버와 부하 실행 결과를 같은 시간축에서 비교할 수 있으며, 외부 서비스의 자격증명은 실행 환경에서 별도로 주입한다.

## Related Work

- [운영 신뢰성](../README.md) — 개발 검증과 운영 중단 이후 보존하는 경계.
- [Mori / Foryn 지원 서버](../../README.md) — 저장소와 외부 연동이 연결되는 전체 기능.
- [지원형 대화](../../supportive-conversations/README.md) — 관계 상태와 메시지 저장을 함께 사용하는 기능.
- [리포트 지연과 연결 점유 비교](./report-pool-experiments/README.md) — 느린 자료 조회가 다른 조회에 전파되는지 확인하는 통제 실험.

## Description

관계형 저장소와 메시지 저장소, 수집 지표, 대시보드 상태는 각각 영속 볼륨에 저장한다. 컨테이너를 중지하거나 재생성해도 볼륨을 유지하면 데이터를 재사용한다. 호스트에 공개하는 포트는 로컬 접근으로 제한하며 비밀번호 누락은 실행 전에 드러난다. 관계형 스키마는 이력 기반 마이그레이션으로 반영하고 서버는 결과를 검증한다.

문서 저장소는 인증을 유지한 단일 노드 복제 구성으로 실행해 다중 문서 트랜잭션을 지원한다. 내부 인증 키는 시작 시 생성해 별도 영속 볼륨에만 보관한다. 준비 확인은 쓰기 가능한 노드인지 읽기만 하며, 초기 구성과 재시작 보존을 구분해 검사한다. 이 로컬 구성은 다중 노드 장애 전환의 검증을 대신하지 않는다.

지표 수집기는 호스트 서버를 주기적으로 조회한다. 서버가 실행되지 않은 동안 수집 실패 상태를 표시한다. 대시보드에는 API 요청량·지연·서버 오류, 메모리, 애플리케이션의 관계형 DB 연결 풀이 나타난다. DB 서버 자체의 내부 지표를 수집하는 구성은 포함하지 않는다.

~~~mermaid
sequenceDiagram
    participant Developer as 개발자
    participant Load as 부하 실행기
    participant App as 로컬 서버
    participant Metrics as 지표 저장소
    participant Dashboard as 대시보드
    Developer->>Load: 조회 경로·부하 크기·실행 식별자 지정
    Load->>App: 간격을 둔 조회 요청
    App-->>Load: 응답 상태와 지연
    Load->>Metrics: 실행별 측정 결과
    Metrics->>App: 서버 지표 수집
    Dashboard->>Metrics: 같은 시간대의 서버·부하 결과 조회
~~~

기본 부하는 단일 가상 사용자의 짧은 상태 확인이며 업무 API 성능을 대변하지 않는다. 인증된 조회를 측정하려면 기존 로컬 계정의 접근 자격을 별도로 주입한다. 실행기는 로컬 주소와 조회 경로를 검증하고, 리다이렉트를 따르지 않으며, 응답 본문과 원본 URL을 측정 자료에 남기지 않는다. 부하 크기와 기간에 기본 상한을 두고 응답 검사·실패율·지연 기준을 위반하면 실패로 종료한다.

구성 검증은 비밀번호 누락, 로컬 포트 경계, 영속 저장, 읽기 전용 설정, 수동 부하 실행을 확인한다. 부하 옵션 검증은 대상 주소·경로·부하 크기와 자격증명 처리 경계를 확인한다. 실제 지표 수집과 대시보드 표시는 컨테이너·서버 기동 후 별도로 확인한다.

개인 실행 자료는 `.local/` 바로 아래에서 주제로 구분한다. 리포트 실험은 `.local/report/experiments/`, 환경 관측 기록은 `.local/workspace/experiments/`에 보관한다. 공유 설명은 이 기록 없이 읽을 수 있어야 한다.

## Conclusion

리포트의 연결 점유는 별도의 일회성 저장소에서 비교한다. 같은 응답을 만드는 네 가지 처리 방식에 정상·지연 조건을 적용하고, 연결 소유권과 실제 조회 횟수, 다른 조회의 대기·오류를 기록한다. 운영 화면 전체의 성능과 통제 실험의 관측값을 구분한다.

남은 연결 대기는 선택 진단 모드에서 JDBC 호출, JVM 대기와 DB 서버 처리 시간을 대조한다. 진단 계측 결과는 기본 부하 표본과 분리한다.

연결 확인은 획득보다 짧은 제한을 사용하고 이 관계를 회귀 검사한다. 실행 결과에는 실제 드라이버 버전과 제한값도 포함한다.

이 구성은 개발자가 작은 로컬 조회부터 시작해 서버 상태와 클라이언트 관측값을 함께 확인하도록 돕는다. 기본 성공 기준은 로컬 확인을 위한 시작값이며 운영 성능 보장이나 과거 운영 연결의 재개를 의미하지 않는다.
