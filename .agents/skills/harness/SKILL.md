---
name: harness
description: 이 저장소의 선택 phase/step 실행 계획을 설계하거나 scripts/execute.py의 사용 조건을 확인할 때 사용한다.
---

# 선택 phase/step 하네스

일반 작업은 루트 [AGENTS.md](../../../AGENTS.md)를 따른다. 사용자가 독립된 헤드리스 세션의 순차 실행을 요청할 때만 이 절차를 적용한다.

## 현재 사용 조건

현재 공유본에는 `phases/`가 없다. 실행기는 아래 제약을 갖고 있으므로 먼저 해결한 뒤 사용한다.

- `scripts/execute.py`는 `feat-{phase}` 브랜치와 영문 소문자 커밋을 만든다. README의 브랜치·한글 커밋 규칙과 다르다.
- 루트 `docs/*.md` 전체를 각 step 문맥에 넣는다. 작업별 문서 선택이 필요하면 실행기를 먼저 개선한다.
- `git add -A`와 자동 커밋을 수행한다. `--push`는 원격에도 반영한다. 다른 작업이 섞인 checkout에서 실행하지 않는다.
- 실행기는 Codex의 승인·샌드박스를 생략하는 옵션을 사용한다. 문서 정리·검증을 위해 실제 실행하지 않는다.

실행기 변경은 TDD guard와 기존 `scripts/test_execute.py` 검사를 따른다. 이 스킬의 지침만으로 실행기 동작이 바뀌지는 않는다.

## 계획 파일

계획이 필요하면 먼저 범위·단계·인수 기준 초안을 제시하고 피드백을 받는다. phase 파일 생성은 계획 승인 후 진행한다. 실제 계획을 만들 때만 `phases/`를 만든다.

최상위 `phases/index.json`:

```json
{"phases": [{"dir": "task-name", "status": "pending"}]}
```

작업별 `phases/{task-name}/index.json`:

```json
{
  "project": "foryn",
  "phase": "task-name",
  "steps": [
    {"step": 0, "name": "module-boundary", "status": "pending"}
  ]
}
```

번호는 0부터 시작한다. 이름은 kebab-case로 쓴다. 생성 시 timestamp를 넣지 않는다.

## Step 계약

`phases/{task-name}/step{N}.md`에는 다음 항목을 넣는다.

1. 읽을 파일: 루트·대상 경로의 AGENTS, 필요한 아키텍처·ADR, 이전 step 산출물. 경로는 저장소 루트 기준이다.
2. 작업: 하나의 모듈 또는 계층 범위, 입출력 경계, 무결성·보안·멱등성 제약.
3. 인수 기준: 복사해서 실행할 수 있는 테스트·구조 검사 명령. 루트 검증 규칙을 반복하지 않고 적용한다.
4. 완료 보고: 다음 step에 유용한 변경 파일·산출물·결정 한 줄.
5. 실패 처리: 수정 3회 후에도 실패하면 error, 사용자 개입이 필요하면 blocked를 기록하고 중단한다.

실행 에이전트는 step 상태와 아래 메시지만 기록하고, 실행기는 timestamp와 최상위 상태를 관리한다.

| 상태 | 메시지 | 실행기 timestamp |
| --- | --- | --- |
| completed | summary | completed_at |
| error | error_message | failed_at |
| blocked | blocked_reason | blocked_at |

error/blocked에서 재개하려면 원인을 해결하고 해당 step을 pending으로 되돌린 뒤 실패 필드를 제거한다. 사용 조건을 해결하고 승인된 계획을 실행할 때의 명령은 `python3 scripts/execute.py {task-name}`이다. push는 원격 반영이 요청된 경우에만 추가한다.
