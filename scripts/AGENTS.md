# 스크립트 변경 규칙

반복 실행의 결과와 상태 변경을 예측할 수 있게 한다. 새 스크립트는 단위 테스트를 먼저 추가한다.

## 실행 계약

- Python은 shebang·argparse·명확한 exit code를 사용한다. Shell은 기본 `set -euo pipefail`을 사용하고 POSIX sh 전용이면 파일 상단에 적는다.
- 필수 환경변수·인자를 검증한다. subprocess는 argv 목록을 사용하고 `shell=True`가 필요하면 이유를 남긴다.
- stdout은 JSON·단일 상태 등 결과, stderr는 진행·경고·사람용 로그로 사용한다.
- JSON은 `ensure_ascii=False`, 안정적인 key와 끝 줄바꿈을 유지한다.

## 상태·권한·수명

- 반복 실행은 같은 상태로 수렴하게 한다. state·phase index 변경 전에는 대상과 변경 의미를 출력한다.
- 일회성 보정은 phase 산출물 또는 `scripts/archive/`에 두고 runbook을 남긴다.
- 데이터 삭제·원격 변경·push는 명시 대상과 dry-run 없이 실행하지 않는다. 운영 변경은 허용 대상과 runbook이 있어야 한다.
- 변수 검증 없는 재귀 삭제, 비밀값 출력·실값 fixture, 저장소 내 자격증명 캐시를 금지한다.
- 기본 동작은 dry-run 또는 읽기 전용으로 한다. `execute.py`는 단계 실행·커밋 보조로 제한한다. 사용 조건은 [harness 스킬](../.agents/skills/harness/SKILL.md)을 따른다.
- 폐기 예정 스크립트는 `_deprecated_` 접두사와 제거 예정일을 남기고 다음 정리에서 삭제한다. 생성 파일·캐시는 커밋하지 않는다.
