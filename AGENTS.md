# foryn

작업 전에 이 파일과 대상 경로의 하위 `AGENTS.md`를 읽는다. 이 파일은 공통 불변식과 작업 순서를 정의한다. 기술 스택과 협업 형식은 [README](README.md)를 따른다.

## 현재 상태와 탐색

- 운영 연결과 자격증명을 제거한 개발 소스 복사본이다. 연결된 운영 환경과 자동 배포는 없다.
- 필요한 문서는 [문서 안내](docs/README.md)에서 선택한다. 현재·과거 실행 구성과 외부 연동은 [아키텍처](docs/ARCHITECTURE.md)를 따른다.
- 이슈·실행 계획은 Git에서 제외하는 `.local/issues/`에 둔다. 스킬의 기본 이슈 추적기보다 [로컬 이슈 규칙](docs/AGENTS.md#로컬-이슈)을 우선한다.
- 단일 문맥으로 다룬다. 제품 범위는 `docs/PRD.md`, 설계 결정은 `docs/ADR.md`를 사용한다. `GLOSSARY.md`가 있으면 확정된 용어를 따른다. 탐색·기록은 [문서 작성 규칙](docs/AGENTS.md)을 따른다.

## 공통 불변식

- TDD guard를 우회하지 않는다. 새 기능·버그 수정은 실패 테스트 → 구현 → 성공 확인 순서로 진행한다.
- 비밀값을 코드에 넣지 않는다. GitHub Secrets, 환경변수, Secret Manager, base64 주입으로만 다룬다.
- `domain`/`entity`에서 외부 SDK를 직접 호출하지 않는다. `client`/`config`/`service` 경계를 둔다.
- Controller에서 Repository를 직접 호출하지 않는다. ArchUnit 규칙을 유지한다.
- 로그·Sentry·메트릭에 PII, 토큰, 결제 원문을 남기지 않는다.
- DB 변경은 Flyway migration으로 반영하고 rollback 또는 보상 방안을 기록한다.
- 실패한 CI를 머지하지 않는다. hotfix도 같은 실패를 막는 테스트를 추가한다.
- 인증·구독·알림 변경은 PR 리뷰 1명 또는 `/grill-me` 자가 검증과 `/review` 증거가 있어야 머지한다.

## 작업과 검증

1. 코드 작성 전에 `python scripts/tdd-guard start --topic "작업 주제"`를 실행한다. 실패 테스트를 `check`로 확인한 뒤 구현하고 `green`으로 완료한다. 플랫폼별 명령은 [검증 안내](docs/development/testing.md#tdd-guard)를 따른다.
2. 검증은 최소 `./gradlew test`를 실행한다. 구조·정적 변경은 `lint archTest`, 설정·배포 영향은 `build`와 관련 검증 스크립트를 추가한다. 변경 도메인의 단위·경계 테스트를 추가하거나 갱신한다.
3. 커밋 전 `.githooks/pre-commit`의 `./gradlew lint build test`를 통과한다. 사용자가 명시적으로 `SKIP_PRE_COMMIT_CHECKS=1`을 지정한 로컬 커밋만 예외로 허용하며, 대체 검증 또는 미검증 사유를 final/PR에 남긴다.
4. 커밋 제목은 README의 `태그: 한글 커밋 메시지`, PR은 `[Type] 제목`과 [PR 템플릿](.github/pull_request_template.md)을 따른다. Lore 본문·trailer로 제목 규칙을 대체하지 않는다.
5. 실행하지 못한 검사는 final/PR에 `Not-tested`로 명시한다. 설계 자가 검증은 `/grill-me`, 로컬 변경 리뷰는 `/review`를 사용한다.

## 운영 변경 조건

- 1인 운영을 가정한다. 실제 담당자·온콜 채널은 저장소 밖 운영 문서가 기준이다. 인증·구독·알림은 SEV1 가능 영역, 채팅·리포트는 SEV2, 문서·하네스는 SEV3이다.
- 실제 알람 채널이 정해지기 전까지 야간 자동 복구를 가정하지 않는다. 안전한 실패와 재시도를 우선한다.
- 운영 데이터 보정은 해당 runbook을 따른다. 없으면 절차와 되돌리기 방안을 먼저 기록한다. 사고 후 재사용할 절차·결정은 runbook/ADR로 남긴다.
- 트래픽 피크/RPS가 문서화되기 전까지 비용·쿼터 변경은 10% 이하 트래픽 또는 dev 환경에서 계측한 뒤 확대한다.
- 100% 트래픽을 즉시 전환하지 않는다. Cloud Run revision/canary 또는 즉시 rollback 가능한 단계를 둔다.

## 도구와 보호 경로

| 대상 | 위치·사용 기준 |
| --- | --- |
| 프로젝트 스킬 | `.agents/skills/`에서 작업에 맞는 스킬만 읽는다. |
| 선택 phase/step 실행 | [harness 스킬](.agents/skills/harness/SKILL.md). 일반 작업의 필수 절차가 아니다. |
| TDD·커밋 관문 | `scripts/tdd-guard`, `.codex/hooks/`, `.githooks/pre-commit`. [검증 안내](docs/development/testing.md)를 따른다. |
| 비밀·인증서 원본 | `src/main/resources/**/*.p8`, `src/main/resources/**/*firebase*`, `src/main/resources/certs/*`는 자동 편집·출력 금지. |
| 실행 state | `.omx/state/*`는 CLI/MCP로만 변경한다. 수동 편집 금지. |
| 생성물 | `build/`, `.gradle/`, `__pycache__/`는 커밋 금지. |

<!-- cluedoc:start -->
## 기능 문서 동기화

코드 변경 후 **cluedoc** 스킬로 영향을 받은 `.cluedoc/`의 부모·자신·자식 문서를 갱신한다. 시스템 동작 질문에는 관련 문서를 확인하고 짧은 Reading Guide를 붙인다.
<!-- cluedoc:end -->
