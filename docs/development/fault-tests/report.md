# 리포트 지연·연결 풀 비교 실험

[검증 안내](../testing.md)의 선택 실행 검사다. 모든 명령은 저장소 루트에서 실행한다.

실제 MySQL·MongoDB와 현재 주간 감정 리포트 서비스를 사용해 연결 점유, 조회 횟수, 혼합 HTTP 부하를 비교한다. 과거 실행 기록을 바탕으로 만든 반복 조회 구현과 현재 기간·배치 조회 구현을 같은 데이터에서 실행한다. 과거 운영 revision 자체를 복원한 실험은 아니다.

검증 코드는 `src/faultTest/java/com/forA/chatbot/report/fault/`에 있으며 실행 JAR에 포함되지 않는다. `reportFaultTest`는 선택 실행이고 `test`, `build`, `outboxFaultTest`에 포함되지 않는다. 일반 애플리케이션 설정과 운영 자격증명을 읽지 않는 최소 Spring/JPA/Mongo 문맥을 사용한다.

## 실행

JDK 21, Python 3.12, Docker Linux 엔진, Gradle 의존성과 `mysql:8.4`, `mongo:8.0` 이미지가 필요하다. 먼저 상태와 실행 계획을 조회한다.

```powershell
docker version
docker image inspect mysql:8.4 mongo:8.0 --format '{{.RepoTags}} {{.Id}}'
python scripts/report_fault.py
```

실행 계획의 기본 동작은 읽기 전용이다. 실제 비교는 다음 명령으로 시작한다.

```powershell
python scripts/report_fault.py --execute
```

실행기는 로컬 Docker 소켓·named pipe만 허용하고 원격 엔진을 거부한다. 볼륨 없는 일회성 DB 두 개를 만들고 루프백 임의 포트만 공개한다. 기존 Compose DB, 계정, 비밀과 데이터를 재사용하지 않는다. 빈 암호는 일회성 MySQL에만 적용한다. MySQL 스키마는 기존 Flyway migration으로 준비하며 합성 자료를 추가한다. MongoDB에는 합성 세션과 감정 코드만 넣는다. 생성한 컨테이너는 이름과 소유 라벨을 확인한 뒤 종료·자동 제거한다. 강제 종료 등으로 정리가 실패하면 `run.json`의 `cleanupErrors`를 확인한다.

최초 구성 확인용 짧은 실행은 다음과 같다. 성능 비교에 사용할 표본 수는 기본 실행보다 적다.

```powershell
python scripts/report_fault.py --execute --repetitions 1 --seconds 2 --delay-ms 150
```

기본 실행은 비교군 4개 × 지연 조건 2개 × 반복 3회다. 각 조건에서 6초간 요청을 발행하고 진행 중 요청이 모두 종료될 때까지 수집한다. DB 기동·컴파일·검증을 포함해 수 분 걸릴 수 있다. 반복은 1–5회, 발행 시간은 2–30초, 명령당 지연은 25–500ms 범위로 제한한다.

남은 연결 획득 실패를 진단하려면 현재 구현과 Mongo 지연 0 조건을 반복한다.

```powershell
python scripts/report_fault.py --execute --diagnose --repetitions 10
```

진단 모드는 1–20회 반복한다. JDBC 연결 설정·실행·반환 시간을 요청별로 기록하고, JFR로 소켓·잠금·스레드 대기와 JVM 정지 시간을 수집한다. 일회성 MySQL의 Performance Schema에서 서버 처리 시간과 오류 번호도 수집한다. SQL 원문은 저장하지 않는다. JFR은 허용한 이벤트만 활성화하며 환경변수와 시스템 속성 이벤트는 포함하지 않는다. 진단 계측에는 추가 비용이 있으므로 기본 비교 결과와 직접 합산하지 않는다.

Docker 없이 입력 경계와 결과 집계만 확인하려면 다음을 실행한다.

```powershell
python -m unittest discover -s scripts -p test_report_fault.py -v
.\gradlew.bat reportFaultTest --tests '*ReportFaultSupportTest' --no-daemon
```

## 비교군과 지연

| 비교군 | 감정 자료 조회 | 서비스 전체 트랜잭션 |
| --- | --- | --- |
| A_REPEAT_TX | 날짜별 세션·복약, 세션별 메시지 반복 조회 | 있음 |
| B_REPEAT_NO_TX | A와 같은 조회 | 없음 |
| C_BATCH_TX | 현재 기간·배치 조회 | 있음 |
| D_BATCH_NO_TX | 현재 기간·배치 조회 | 없음 — 현재 서비스 |

모든 비교군은 같은 MySQL 풀, 데이터, HTTP 실행기와 자원을 사용한다. 트랜잭션 유무 비교는 A↔B, C↔D로, 조회 구조 비교는 A↔C, B↔D로 수행한다. 트랜잭션이 있는 비교군은 실제 JPA 트랜잭션 관리자의 읽기 전용 경계를 적용한다.

Mongo 읽기 명령을 드라이버에 전달하기 직전, 리포트 요청 스레드에만 일정한 대기를 넣는다. 실제 Mongo 조회는 그대로 실행한다. 명령별 지연이므로 반복 조회 쪽의 총 대기는 더 길다. 클라이언트 경계에서 느린 조회를 모사하며 Mongo 서버 처리나 네트워크 패킷 자체를 늦추는 실험은 아니다.

합성 자료는 14일·대화 세션 28개·감정 메시지 28개·복약 기록 14개다. 빈 기간 때문에 메시지 조회가 생략되지 않도록 한다. 네 비교군의 전체 주간 응답이 같은지 검사한 뒤, 워밍업과 구분한 단일 요청에서 실제 실행 횟수를 비교한다. 자정 경계를 넘는 실행은 날짜 범위가 달라질 수 있으므로 같은 날짜 안에서 실행한다.

## 연결 소유권 회귀 검사

혼합 부하와 별도로 첫 Mongo 명령에서 리포트 4개를 동시에 정지한다. 같은 배치 조회에서 트랜잭션만 바꾼 C와 D를 비교한다.

~~~mermaid
sequenceDiagram
    participant Reports as 리포트 4개
    participant Pool as MySQL 풀 4개
    participant Gate as Mongo 명령 대기
    participant Ordinary as 일반 조회
    Reports->>Gate: 첫 Mongo 조회에서 정지
    Note over Reports,Pool: 전체 트랜잭션 유무에 따른 실제 연결 소유권 기록
    Ordinary->>Pool: 같은 풀에서 사용자 조회
    Pool-->>Ordinary: 연결 획득 또는 300ms 제한 후 실패
    Gate-->>Reports: 대기 해제 후 실제 Mongo 조회 계속
~~~

- C: 대기 중 active=4, 리포트별 연결 소유 1개, 일반 조회 HTTP 500.
- D: 대기 중 active=0, 리포트별 연결 소유 0개, 일반 조회 HTTP 200.
- 대기 해제 후 리포트가 모두 정상 종료하고 연결을 반환해야 한다.

이 검사는 트랜잭션 존재만 확인하는 어노테이션 검사가 아니다. 실제 연결 대여·반납과 Hikari 상태를 확인한다. 조건을 만족하지 않으면 테스트가 실패한다.

## 혼합 HTTP 부하와 계측

검증 전용 `/report`는 실제 주간 감정 서비스 또는 비교 구현을 실행하고, `/mysql`은 실제 사용자 저장소 조회를 실행한다. 두 경로는 같은 Hikari 풀을 사용한다. HTTP 서버는 루프백에만 바인딩하며 Spring MVC, JWT 인증, OSIV와 외부 서비스는 실행하지 않는다. 따라서 전체 운영 API 성능 측정을 대신하지 않는다.

- 풀 크기 4, 연결 획득 제한 300ms, 연결 확인 설정 250ms, auto-commit 활성 상태를 모든 비교군에 동일 적용한다. 연결 확인은 고정 쿼리 `SELECT 1`을 사용한다. JDBC 연결 확인 API의 초 단위 제한이 250ms를 덮어쓰는 것을 피한다. 실제 MySQL 응답을 600ms 지연하는 회귀 검사로 검증한다. 이 값과 방식은 로컬 실험 조건이며 운영 권장값이 아니다.
- 리포트 16회/초, 일반 조회 8회/초를 독립된 일정으로 발행한다. 응답이 늦어져도 발행률을 낮추지 않는다.
- 각 반복의 조건 순서는 고정 seed로 섞는다. 발행 지연도 기록해 부하 실행기 지연을 확인한다.
- p95는 HTTP 클라이언트가 관측한 시간이며 실패 응답도 포함한다. 반복 실행의 원본 표본을 합쳐 계산하며 실행별 p95를 평균하지 않는다.
- SQL 횟수는 요청 계측의 실제 JDBC `execute*` 호출이다. ORM 연관 자료의 추가 조회도 포함한다. 풀 내부의 `SELECT 1` 연결 확인과 연결 설정·커밋 같은 내부 명령은 이 횟수에서 제외한다. 진단 모드의 MySQL 서버 명령 자료에서 연결 확인 쿼리를 별도 분류한다. Mongo는 읽기 명령과 추가 결과 수집 명령을 센다. 명령 시작 횟수이므로 실패 시도·드라이버 재시도가 포함될 수 있다.
- 요청별 JDBC 획득 시간, 연결 사용 시간, 획득 실패, Mongo 대기 중 연결 소유 수를 기록한다. 연결 사용 시간은 풀에서 빌린 시점부터 반환 시점까지다.
- 연결 active·pending 최대치는 5ms 간격 표본이다. 표본 사이의 짧은 최고값까지 보장하지 않는다.
- Hikari 기본 메트릭도 남긴다. 기본 메트릭은 실행 전체 누적값이고 비교군별 수치는 별도 요청 계측에서 계산한다.
- SQL 원문, 문서 내용, 응답 본문과 토큰은 측정 파일에 저장하지 않는다.

회귀 검사는 현재 비교군 D가 Mongo 대기 중 JDBC 연결을 점유하지 않는지, 요청이 누락되지 않는지, 요청별 획득 실패 합계와 Hikari 타임아웃 카운터가 일치하는지 판정한다. HTTP 오류율은 모든 비교군에서 그대로 기록하고 현재 비교군의 오류도 별도 표시한다. 오류 0을 이 기계의 성능 기준으로 임의 지정하지 않는다. 실제 조회 감소는 성공한 단일 요청에서 판단한다. 과부하로 일찍 실패한 요청의 낮은 조회 횟수를 개선으로 해석하지 않는다.

## 결과

실행 기록은 `.local/report/experiments/fault-runs/`, 원본 관측값과 비교 결과는 `.local/report/experiments/fault-evidence/`에 보관한다. 모두 Git에서 제외한다.

| 파일 | 내용 |
| --- | --- |
| `report-fault-runs/<ID>/gradle.log` | 전체 검사 출력 |
| `report-fault-runs/<ID>/run.json` | 종료 코드, 증거 경로, 컨테이너 정리 오류 |
| `report-fault-evidence/<ID>/comparison.md` | 연결 소유권, 조회 횟수, 혼합 부하 비교표 |
| `environment.json`, `infrastructure.json` | JVM·DB·풀·데이터·Docker·이미지·소스 지문과 실험 조건 |
| `gates.json`, `query-counts.json` | 연결 소유권 검사와 성공 요청의 실제 조회 횟수 |
| `summary.json` | 실행 조건별 지연·오류·풀 최대치 |
| `clients.ndjson` | HTTP 시간·발행 지연·상태 원본 |
| `requests.ndjson` | 요청별 SQL·Mongo 횟수·연결 시간·획득 실패 |
| `events.ndjson` | 요청·연결·SQL 실행·Mongo 대기 타임라인 |
| `hikari-metrics.json` | Hikari 기본 메트릭 누적값 |
| `observed-errors.json` | 현재 비교군에서 관측한 HTTP 오류와 연결 획득 실패 |
| `mysql-statements.json` | 진단 모드의 MySQL 명령별 처리 시간·잠금 시간·행 수·오류 번호 |
| `diagnosis.jfr`, `jfr-events.json` | 진단 모드의 JDBC 호출·JVM·소켓 대기 기록과 조회용 변환본 |

진단 모드에서는 `requests.ndjson`의 `jdbcCalls`와 `events.ndjson`의 JDBC 시작·종료 신호도 기록한다. 연결 획득 실패만으로 느린 SQL, Mongo 연결 점유, JVM 정지 중 하나를 원인으로 정하지 않는다. 같은 시간대의 연결 소유자와 실제 지연 구간을 대조한다.

실행 조건에는 실제 JDBC 드라이버 버전과 연결 확인 제한도 포함한다. 빌드 파일에 선언한 버전과 실제로 로드한 버전을 구분한다. 과거 실행 결과를 읽을 때는 해당 실행의 소스 지문과 제한값을 먼저 확인한다.

느린 Mongo에서도 일반 MySQL 조회가 보호되는지와 리포트 지연 자체가 줄었는지를 구분한다. OSIV는 모든 비교군에서 없으며, 현재 애플리케이션의 기본 HTTP 환경과 다를 수 있다. 과거 운영의 단일 원인, 개선 당시 성능 수치, 모든 트래픽에서의 무재발은 이 실험만으로 확정하지 않는다.
