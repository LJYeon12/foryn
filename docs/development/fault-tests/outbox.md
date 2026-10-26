# 로컬 Outbox 장애 재현

[검증 안내](../testing.md)의 선택 실행 검사다. 모든 명령은 저장소 루트에서 실행한다.

실제 MySQL과 독립 JVM 두 개에서 복약 계획 저장·예약 등록·발송 전 검사를 실행한다. 운영 코드의 저장소와 서비스는 그대로 사용하고, 작업 등록과 푸시 전송 경계만 루프백 HTTP 대역으로 교체한다. GCP 자격증명, MongoDB, 실제 기기는 필요하지 않다.

이 검사는 선택 실행이다. `test`, `integrationTest`, `build`의 성공이 아래 실험의 실행을 의미하지 않는다. 일반 애플리케이션 설정 파일과 운영 자격증명을 읽지 않는 최소 Spring/JPA 문맥을 별도 JVM에서 기동한다. HTTP 컨트롤러·OIDC 필터·Google SDK·실제 Cloud Tasks 실행기는 이 실험의 범위 밖이다.

## 준비와 실행

JDK 21, Docker, 프로젝트 Gradle 의존성이 필요하다. 먼저 Docker와 이미지 상태를 조회한다.

```powershell
docker version
docker image inspect mysql:8.4
```

기존 DB에 연결하지 않는다. 아래 명령은 볼륨 없이 검증 전용 MySQL을 만들고 루프백 임의 포트만 공개한다. 빈 암호는 이 일회성 DB에만 사용하며, 기존 사용자나 비밀을 재사용하지 않는다.

```powershell
$outboxContainer = 'foryn-outbox-fault-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
docker run --rm --detach --name $outboxContainer --label foryn.purpose=outbox-local-fault-check --publish 127.0.0.1::3306 --env MYSQL_ALLOW_EMPTY_PASSWORD=yes --env MYSQL_DATABASE=outbox_fault_local mysql:8.4
docker port $outboxContainer 3306/tcp
docker exec $outboxContainer mysqladmin ping --silent
```

준비가 끝날 때까지 마지막 조회를 반복한다. 준비 확인 후 포트를 읽어 실행한다.

```powershell
$outboxBinding = (docker port $outboxContainer 3306/tcp).Trim()
if ($outboxBinding -notmatch '^127\.0\.0\.1:(\d+)$') { throw 'Unexpected binding' }
$outboxPort = $Matches[1]
$env:OUTBOX_FAULT_JDBC_URL = "jdbc:mysql://127.0.0.1:$outboxPort/outbox_fault_local?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC"
.\gradlew.bat outboxFaultTest --no-daemon
```

접속 문자열은 `127.0.0.1`의 `outbox_fault_*` 스키마와 정해진 JDBC 옵션으로 제한한다. 검사 자체가 Flyway로 V1부터 현재 버전까지 적용하고 검증한다. 해당 스키마에는 합성 데이터를 추가하므로 반드시 검증 전용 빈 DB를 사용한다.

예정 시각은 서울 날짜의 23:59다. 날짜 경계로 인한 혼선을 피하기 위해 서울 시각 23:50 이후에는 사전 조건에서 실패한다. 실제 1분 재시도 대기와 실제 5분 처리권 만료를 포함하므로 수 분 이상 걸린다. 자식 JVM을 강제 종료하는 실험이 있으므로 실행 중인 작업을 임의로 중단하지 않는 편이 좋다.

## 실험과 판정

| 실험 | 주입한 상황 | 판정 |
| --- | --- | --- |
| 실제 DB 롤백 | 서비스에서 계획·작업 저장 후 상위 트랜잭션 실패 | 두 테이블의 건수 모두 원래 값 |
| 커밋 후 종료 | 작업 저장 후 등록 전에 JVM 강제 종료 | 새 JVM이 메모리 신호 없이 작업을 찾아 등록 |
| 일시적 등록 실패 | 로컬 등록 서버가 503 반환 | 재시도 대기 상태 보존, 즉시 중복 호출 없음, 실제 1분 경과 후 완료 |
| 응답 유실 | 등록 대역은 수락하지만 HTTP 응답 전 연결 종료 | 동일 ID 재시도, 대역의 중복 응답 처리 후 완료 |
| 다중 프로세스 경쟁 | 두 JVM이 같은 DB 작업 동시 선점, 등록 호출을 잠시 차단 | 10개 작업 각각 시도 횟수 1·등록 호출 1 |
| 변경·삭제·중복 요청 | 변경 전 요청 실행, 삭제 후 요청 실행, 현재 버전 두 번 실행 | 이전·삭제 요청의 푸시 대역 호출 0, 현재 요청은 합계 1 |
| 재시도 소진 | 등록 4회 실패, 추가 자동 처리 요청 | 실패 상태 보존, 다섯 번째 등록 없음, 명시적 재시도로 복구 |
| 처리 중 종료 | 선점 후 등록 완료 전에 JVM 강제 종료 | 실제 5분 처리권 만료 전 회수 안 함, 만료 후 다른 JVM이 완료 |

응답 유실·시도 소진 실험의 재시도 시각만 SQL로 앞당긴다. 타이머 정확성은 별도의 실제 대기 실험에서 확인한다. 처리권 만료 실험은 만료 시각을 수정하지 않는다. 로컬 대역의 중복 응답은 검사자가 정한 계약이며 실제 GCP의 응답을 확인한 결과가 아니다.

빠른 경계 검사만 실행하려면 DB 없이 다음을 사용할 수 있다.

```powershell
.\gradlew.bat outboxFaultTest --tests '*LocalTaskTransportTest' --no-daemon
```

## 결과와 정리

- 판정: `build/reports/tests/outboxFaultTest/index.html`, `build/test-results/outboxFaultTest/`.
- 과정 증거: `build/outbox-fault-evidence/<실행 ID>/observations.json`, `receiver-state.json`, JVM별 로그.
- 로그의 사용자·약 이름·기기 값은 합성 데이터다. 푸시 대역에는 실제 또는 합성 토큰·메시지 본문을 전송하지 않는다.
- 실패한 실험은 JUnit 보고서에 남는다. 과정 파일의 일부 성공 단계만으로 전체 성공을 주장하지 않는다.
- 생성물은 Git에 올리지 않는다. 특정 실행 수치와 포트폴리오 준비 기록은 `.local/outbox/`에 보관하고, 실행 코드·로그·원본 결과는 그 아래 `experiments/`로 구분한다.

검증 후 정확한 컨테이너와 목적 레이블을 확인한 뒤 종료한다. `--rm`으로 해당 컨테이너만 자동 제거된다.

```powershell
docker inspect $outboxContainer --format '{{.Name}} {{index .Config.Labels "foryn.purpose"}} {{.Config.Image}}'
# 위 결과가 방금 만든 검증 컨테이너·목적·mysql:8.4인지 확인한다.
docker stop $outboxContainer
Remove-Item Env:OUTBOX_FAULT_JDBC_URL
```

Outbox 등록 완료는 FCM 전송 성공 또는 기기 수신 성공이 아니다. 실제 GCP IAM/OIDC, 큐 설정·재전송 정책, Cloud Scheduler 연결, FCM 기기 전달, 운영 부하는 별도로 검증한다.

## 내부 인증과 SDK 어댑터의 로컬 검사

DB·Docker·GCP 자격증명 없이 일반 테스트로 다음 경계를 확인할 수 있다.

```powershell
.\gradlew.bat test --tests '*LocalOidcSecurityTest' --tests '*CloudTasksClientTest' --tests '*InternalNotificationSecurityTest' --tests '*OidcTokenFilterScopeTest' --no-daemon
```

`LocalOidcSecurityTest`는 테스트 중 메모리에 만든 RSA 키로 토큰을 서명한다. 실제 내부 보안 체인과 Google 토큰 검증기를 실행한다. 검증기 생성 지점만 테스트 범위에서 대체하여 공개키를 공급한다. 운영 필터가 설정하는 발급자·audience 조건은 그대로 사용한다. 검증기와 서명 판정을 성공 대역으로 바꾸지 않는다.

- 유효한 토큰이 지정 실행 계정으로 인증되고 복구·수동 재시도 서비스에 도달한다.
- 다른 audience·발급자·서명, 만료, 잘못된 형식·알고리즘은 401이다.
- 다른 실행 계정 또는 계정 claim의 누락·타입 오류는 403이다.
- 공개키 조회 실패는 401이며 내부 작업을 실행하지 않는다.
- 거절 로그에는 계정 이메일·토큰 claim 원문 대신 제한된 이유 코드만 남긴다.
- 인증 뒤 서비스에서 발생한 입력 오류는 토큰 오류로 바꾸지 않는다.

`CloudTasksClientTest`는 애플리케이션의 실제 어댑터를 실행하고 Google SDK 클라이언트를 대역으로 바꾼다. 작업의 ID·대상 URL·JSON·예약 시각·OIDC audience·실행 계정과 클라이언트 정리를 검사한다. 이미 존재하는 작업은 성공으로 처리하고, 등록 서비스 장애와 SDK 생성 실패는 호출자가 복구할 수 있도록 전파하는지 확인한다. 기존 삭제 경로의 작업 부재·장애 처리도 검사한다.

이 검사는 Google이 발급한 토큰, 공개키 서버, IAM 권한, 실제 gRPC 통신이나 큐의 동작을 검증하지 않는다. 앞의 다중 JVM 장애 실험은 로컬 HTTP 대역을 쓰므로, 그 결과가 이 SDK 어댑터의 실연동 검증을 대신하지도 않는다. 각각의 검사 범위를 구분해 기록한다.
