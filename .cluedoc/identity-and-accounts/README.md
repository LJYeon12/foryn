---
title: 계정과 인증
sources:
  - src/main/java/com/forA/chatbot/auth
  - src/main/java/com/forA/chatbot/user
  - src/main/java/com/forA/chatbot/config/SecurityConfig.java
  - src/main/java/com/forA/chatbot/config/InternalSecurityConfig.java
  - docs/README.md
  - src/test/java/com/forA/chatbot/notification/controller/LocalOidcSecurityTest.java
---

## Hero Visual

~~~mermaid
flowchart LR
    App[모바일 앱] --> Proof[외부 신원 증거]
    Proof --> Verify{서버 검증}
    Verify -->|유효| Account[사용자 계정]
    Account --> Access[짧은 접근 자격]
    Account --> Renewal[장기 갱신 자격]
    Access --> Protected[보호된 기능]
    Renewal --> Access
    Verify -->|무효| Deny[접근 거부]
~~~

## Abstract

계정과 인증은 외부 신원을 사용자 계정으로 연결하고, 이후 요청의 사용자를 식별한다. 로그인, 접근 자격 갱신, 로그아웃, 프로필 관리, 데이터 초기화와 탈퇴를 사용자 수명 주기로 묶는다. 이메일만으로 자격을 발급하던 임시 로그인은 제거했으며, 정식 로그인 검증의 보완 사항은 남아 있다.

## Introduction

로그인은 Apple 공개키로 신원 증거의 서명을 검증한 뒤 기존 계정을 찾거나 새 계정을 만든다. 발급자와 대상 앱을 명시적으로 검증하는 절차는 현재 누락되어 있다. 인증 없이 접근할 수 있는 사용자 자격 경로는 정식 로그인과 토큰 갱신으로 한정한다. 이메일만으로 자격을 발급하는 경로는 제공하지 않는다.

계정에는 닉네임과 개인화에 필요한 프로필이 연결된다. 사용자는 자신의 프로필을 조회·수정하고 활동 데이터를 초기화하거나 계정을 비활성화할 수 있다. 정식 재로그인 시 비활성 계정을 다시 활성화하는 흐름도 포함된다.

## Related Work

- [Mori / Foryn 지원 서버](../README.md) — 계정 경계가 보호하는 전체 기능 지도.
- [지원형 대화](../supportive-conversations/README.md) — 프로필과 인증된 사용자 맥락을 사용하는 대화 경험.
- [복약 루틴](../medication-routines/README.md) — 계정 소유권으로 격리되는 복약 계획과 기록.
- [리마인더와 알림](../reminders-and-notifications/README.md) — 로그인 상태 및 기기 연결과 함께 관리되는 전달 채널.
- [일기 알림 설정과 재예약](../reminders-and-notifications/diary-reminders/README.md) — 프로필 조회와 수정 응답에 포함되는 알림 설정.
- [구독 권한](../subscription-access/README.md) — 사용자 계정에 귀속되는 결제 권한.

## Description

정식 로그인은 신원 증거를 계정과 접근 자격으로 연결한다. 로그아웃은 현재 접근 자격과 갱신·기기 연결을 정리하며, 탈퇴는 계정 비활성화와 갱신·기기 연결 정리를 수행한다.

~~~mermaid
stateDiagram-v2
    state "검증 전" as Unverified
    state "활성 계정" as Active
    state "로그아웃" as SignedOut
    state "비활성 계정" as Deactivated

    [*] --> Unverified
    Unverified --> Active: 신원 증거 검증
    Active --> Active: 접근 자격 갱신
    Active --> SignedOut: 로그아웃
    SignedOut --> Active: 재로그인
    Active --> Deactivated: 계정 탈퇴
    Deactivated --> Active: 검증된 재로그인
~~~

접근 자격은 요청마다 서명과 만료를 확인한다. 갱신 자격은 서버 저장 상태와 만료 시각이 모두 유효할 때만 새 접근 자격으로 교환된다. 로그아웃하면 현재 접근 자격은 남은 유효 기간 동안 거부 목록에 들어가고, 갱신 자격과 연결된 기기 알림 채널도 정리되어 재사용 경로를 줄인다.

프로필은 대화 온보딩과 개인화의 공통 맥락이다. 조회와 수정 응답에는 현재 일기 알림 설정도 포함된다. 초기화는 대화와 복약 기록·계획을 정리하고 개인화 프로필을 초기 상태로 돌린다. 현재 초기화 흐름에는 일기 기록과 일기 알림 설정의 삭제가 포함되어 있지 않다. 탈퇴는 계정을 비활성화하고 갱신 자격과 기기 토큰을 삭제하는 별도 선택이다.

대화의 프로필 변경은 응답 준비와 실제 저장을 분리한다. 저장 직전에 최신 값과 계정 활성 여부를 다시 확인한다. 초기화·대화 삭제는 진행 중인 대화 작업과 같은 사용자 작업권을 사용하고, 계정 저장이 끝나기 전에 그 작업권을 풀지 않는다. 계정 수명 주기 전체를 두 저장소의 단일 트랜잭션으로 묶은 것은 아니다.

프로필 저장은 실제로 바꾼 항목만 갱신한다. 오래된 닉네임 수정이 대화에서 갱신한 다른 항목을 되돌리지 않게 하기 위해서다. 초기화는 대화 작업권을 확보한 뒤 최신 프로필을 다시 읽고 전체 초기화 값을 적용한다.

보안 실패는 인증 실패와 권한 실패를 구분한다. 임시 로그인 제거로 해당 경로의 이메일 로그도 사라졌지만, 다른 기능의 민감정보 로그 문제는 남아 있다. 원문을 남기지 않는 정책은 운영 전 충족해야 할 요구사항이다. 내부 예약 호출은 사용자 접근 자격과 별도로 발급자·대상·실행 계정을 확인한다.

내부 예약 인증의 로컬 검사는 임시 키로 서명한 증거를 실제 검증기와 보안 체인에 전달한다. 다른 발급자·대상·서명, 만료와 잘못된 형식은 인증 실패로 처리하고, 다른 실행 계정은 권한 실패로 처리한다. 내부 인증 거절 로그는 계정과 증거 원문을 빼고 이유 코드만 남긴다. 이 검사는 실제 외부 계정의 권한 설정이나 공개키 서버 연결을 확인한 결과가 아니다.

## Conclusion

계정과 인증은 개인 기능의 사용자 식별 기반이다. 이메일 기반 임시 로그인은 제거했고 자격 갱신과 기기 정리 흐름은 유지한다. 정식 신원 증거 검증 보완과 민감정보 로그 정리는 운영 수준의 신뢰 경계를 평가하기 전에 해결해야 한다.
