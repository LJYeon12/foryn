# 검증 안내

JDK 21을 사용하고 모든 명령은 저장소 루트에서 실행한다. 기능 변경은 실패 테스트 확인 → 구현 → 성공 확인 순서로 진행한다. 검증 범위와 예외 조건은 [프로젝트 작업 규칙](../../AGENTS.md)을 따른다.

## TDD guard

Python 3가 필요하다. Windows에서는 다음 순서로 실행한다. 테스트를 작성한 뒤 실제 기대 동작의 실패를 `check`로 확인하고 구현한다.

```powershell
python scripts/tdd-guard start --topic "작업 주제"
# 테스트 작성 후 실패 확인
python scripts/tdd-guard check -- .\gradlew.bat test --tests 'com.example.MyTest'
# 구현 후 성공 확인
python scripts/tdd-guard green -- .\gradlew.bat test --tests 'com.example.MyTest'
```

`com.example.MyTest`는 변경할 실제 테스트로 바꾼다. macOS/Linux에서는 `python3`와 `./gradlew`를 사용한다. 문서만 변경할 때는 실패 테스트를 만들지 않는다.

상태 파일 `.omx/state/tdd-guard.json`은 이 CLI로만 갱신한다. `.codex/hooks/project_hook.py`는 유효한 실패 기록이 없을 때 `src/main/**`를 수정하는 알려진 도구 이벤트를 차단한다. 테스트·문서·훅·설정·스크립트는 허용 대상이다. 셸 명령 감지는 휴리스틱이며 보안 경계가 아니다.

훅 정의는 `.codex/hooks.json`, 커밋 검사는 `.githooks/pre-commit`에 있다. 현재 명령은 POSIX 셸과 `/usr/bin/python3`를 사용하므로 Windows에서는 호환 환경과 실행 로그를 확인한다. 설정 파일이 있다는 이유만으로 훅이 실행되었다고 판단하지 않는다. Git 훅 연결은 `git config --get core.hooksPath`로 확인하며, 연결할 때는 `git config core.hooksPath .githooks`를 사용한다. 커밋 검사는 TDD guard와 별도로 실행된다.

CLI·훅·선택 phase 실행기의 변경은 아래 기존 검사로 확인한다. CLI 검사 내부는 `python3`를 호출하므로 Windows에서도 이 명령이 PATH에 있어야 한다. phase 실행기 검사에는 `pytest`가 필요하다.

```powershell
python -m unittest scripts.test_tdd_guard -v
python -m unittest discover -s .codex/hooks -p 'test_*.py' -v
python -m pytest scripts/test_execute.py -q
```

## 일반 검사

Windows:

```powershell
.\gradlew.bat test lint archTest build --no-daemon
```

macOS/Linux:

```bash
./gradlew test lint archTest build --no-daemon
```

| 작업 | 검사 범위 |
| --- | --- |
| `test` | 일반 단위·경계 테스트 |
| `lint` | 운영·테스트 Java 코드의 Checkstyle 규칙 |
| `archTest` | 계층·패키지 경계의 ArchUnit 규칙 |
| `build` | 빌드와 연결된 검사, 실행 JAR 생성 |
| `integrationTest` | 별도로 호출하는 Spring 통합 테스트 |

실행 JAR을 만들 때에는 [Apple 공개 루트 인증서 준비](local-setup.md#apple-공개-루트-인증서-준비)를 먼저 진행한다. 테스트 설정에는 대역 값을 사용하며 운영 자격증명을 주입하지 않는다.

통합 테스트는 별도로 실행한다. Windows 명령은 다음과 같고 macOS/Linux에서는 `./gradlew`를 사용한다.

```powershell
.\gradlew.bat integrationTest --no-daemon
```

PR 워크플로는 현재 `test`만 실행한다. 로컬 커밋 관문은 `lint build test`를 실행한다. `archTest`, `integrationTest`와 아래 장애 검사는 별도로 호출해야 한다. `build`의 성공만으로 이 검사들이 모두 실행되었다고 판단하지 않는다.

## Docker 구성과 부하 옵션

Docker Compose CLI, Python과 Node.js가 필요하다. 아래 검사는 Docker 엔진을 시작하지 않으며 실제 비밀번호를 사용하지 않는다.

```powershell
python -m unittest scripts.test_local_stack -v
node --test scripts/test_k6_options.mjs
```

실제 컨테이너와 서버 기동은 [로컬 실행](local-setup.md), 지표 수집과 k6 결과 수신은 [관측과 부하 확인](observability.md)에서 확인한다.

## 선택 실행 장애 검사

각 검사는 일반 `test`·`build`에 포함되지 않는다. 기존 서비스 DB 대신 검증 전용 저장소와 합성 데이터를 사용한다. 먼저 각 안내의 준비 조건과 실행 계획을 확인한다.

| 대상 | 확인하는 동작 | 실행 안내 |
| --- | --- | --- |
| 채팅 저장·복구 | 부분 저장, 커밋 응답 유실, 보상, 작업자 경쟁과 재시작 복구 | [채팅 장애 검사](fault-tests/chat.md) |
| 복약 예약 Outbox | 계획·작업의 원자적 저장, 등록 실패, 재시도와 처리권 만료 | [Outbox 장애 검사](fault-tests/outbox.md) |
| 리포트 | 조회 지연, 연결 소유권, 조회 횟수와 혼합 HTTP 부하 | [리포트 비교 실험](fault-tests/report.md) |

복약 Outbox와 리포트 안내에는 DB 없이 실행할 수 있는 경계 검사도 포함되어 있다. 로컬 대역의 성공은 실제 Cloud Tasks·FCM·IAM 또는 기기 수신의 성공을 보장하지 않는다.

## 결과 기록

성공·실패는 Gradle/JUnit 보고서와 각 검사의 판정 기준으로 확인한다. 일부 과정 파일만으로 전체 성공을 판단하지 않는다. 실행하지 않은 검사는 결과 보고에 `Not-tested`로 명시한다.

실행별 로그와 원본 측정값의 위치는 [공유 문서와 로컬 기록 규칙](../AGENTS.md#공유-문서와-로컬-기록)을 따른다.
