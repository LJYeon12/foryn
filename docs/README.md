# 문서 안내

처음 읽을 때는 [제품 범위](PRD.md) → [전체 구조](ARCHITECTURE.md) → [로컬 실행](development/local-setup.md) 순서로 본다. 특정 작업 중에는 아래 표에서 필요한 문서로 바로 이동한다.

## 목적별 안내

| 하고 싶은 일 | 문서 |
| --- | --- |
| 서비스의 사용자와 제품 범위 이해 | [제품 요구사항](PRD.md) |
| 모듈·저장소·외부 연동과 신뢰 원천 확인 | [아키텍처](ARCHITECTURE.md) |
| 개발 환경에서 백엔드 실행 | [로컬 실행](development/local-setup.md) |
| 서버 지표와 부하 결과 확인 | [관측과 부하 확인](development/observability.md) |
| 테스트·구조 검사·장애 재현 | [검증 안내](development/testing.md) |
| 에이전트 작업·TDD·로컬 이슈 규칙 확인 | [작업 규칙](../AGENTS.md), [문서·이슈 기록 규칙](AGENTS.md) |
| 설계를 선택한 이유 확인 | [설계 결정 이력](ADR.md) |
| 채팅 미완료 작업 확인·복구 | [채팅 저장·복구 절차](runbooks/chat-recovery.md) |
| 복약 예약 등록 지연·실패 대응 | [Outbox 운영 절차](runbooks/medication-outbox.md) |

현재 실행 환경과 과거 배포 구성은 [아키텍처의 실행 환경](ARCHITECTURE.md#실행-환경과-외부-연동)에 모아 둔다. API 명세는 현재 공유본에 포함되어 있지 않으므로 요청·응답 계약은 해당 Controller·DTO와 테스트로 확인한다.

## 기능별 설명

기능의 현재 동작과 제약은 `.cluedoc/`에서 관리한다. 아래 링크로 필요한 기능을 바로 읽을 수 있다.

| 기능 | 설명 |
| --- | --- |
| [계정과 인증](../.cluedoc/identity-and-accounts/README.md) | Apple 로그인, 서버 접근 자격과 계정 상태 |
| [지원형 대화](../.cluedoc/supportive-conversations/README.md) | 대화 단계와 저장·복구 |
| [일기와 인사이트](../.cluedoc/journaling-and-insights/README.md) | 날짜별 일기와 기간별 감정 집계 |
| [복약 루틴](../.cluedoc/medication-routines/README.md) | 계획, 복약 기록과 연속 실천 |
| [리마인더와 알림](../.cluedoc/reminders-and-notifications/README.md) | 예약, 재시도와 기기 전달 |
| [구독 권한](../.cluedoc/subscription-access/README.md) | Apple 결제 검증과 서버 구독 상태 |
| [운영 신뢰성](../.cluedoc/operational-reliability/README.md) | 변경 검증, 관측과 장애 검사 |

## 문서 관리

| 위치 | 보관 내용 | Git 공유 |
| --- | --- | --- |
| `docs/` | 제품·아키텍처·결정, 개발 안내와 운영 절차 | 포함 |
| `.cluedoc/` | 코드에 근거한 기능별 상세 설명 | 포함 |
| `.local/<주제>/` | 개인 메모·이슈·실행 계획·검증 원본 | 제외 |

문서별 책임, 충돌 시 우선순위와 로컬 이슈 형식은 [문서 작성 규칙](AGENTS.md)을 따른다. 공유 설명은 로컬 전용 기록 없이 이해할 수 있어야 한다.
