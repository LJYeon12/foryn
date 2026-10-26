# 관측과 부하 확인

[로컬 실행](local-setup.md)을 완료한 뒤 진행한다. Prometheus는 호스트의 백엔드 지표와 k6의 remote write 결과를 수집하고, Grafana는 같은 데이터를 표시한다. k6는 `load` 프로필에서 수동으로 실행한다. 모든 명령은 저장소 루트에서 실행한다.

## 1. 지표 확인

백엔드가 8080에서 실행된 뒤 확인한다.

```powershell
(Invoke-WebRequest http://localhost:8080/actuator/prometheus).StatusCode
```

[Prometheus Targets](http://localhost:9090/targets)에서 `foryn`이 `UP`인지 확인한다. 수집 주소는 `host.docker.internal:8080`이며 백엔드 포트를 바꾸면 `infra/observability/prometheus.yml`도 맞춘다. 백엔드가 꺼져 있는 동안 `foryn`이 `DOWN`인 것은 예상된 상태다.

Grafana에는 `.env`의 관리자 계정으로 로그인한다. Prometheus 데이터 소스와 [Foryn / Local 대시보드](http://localhost:3000/d/foryn-local)가 자동 등록된다. 대시보드는 애플리케이션 수집 상태, API RPS·p95·5xx, JVM heap, HikariCP 연결 수와 k6 결과를 보여 준다. 서버 API 패널은 Actuator 요청을 제외한다. MySQL·MongoDB 서버 자체의 내부 지표를 수집하는 exporter는 이 구성에 포함하지 않는다.

## 2. k6 실행

기본 시나리오는 로컬 `/actuator/health`에 1 VU로 30초 동안 GET 요청을 보내며 요청 사이에 1초 쉰다. 이 결과는 연결 확인용이며 업무 API나 DB 성능의 기준으로 사용하지 않는다.

```powershell
$env:K6_TEST_ID = Get-Date -Format yyyyMMdd-HHmmss
docker compose --profile load run --rm k6
```

업무 API 측정 예시는 기존 로컬 테스트 계정의 토큰을 `K6_ACCESS_TOKEN` 환경변수로 주입한 뒤 실행한다. 토큰 값은 스크립트·명령 예시·문서에 기록하지 않는다.

```powershell
$env:K6_TARGET_PATH = '/api/v1/users/me'
$env:K6_VUS = '5'
$env:K6_DURATION = '1m'
$env:K6_TEST_ID = Get-Date -Format yyyyMMdd-HHmmss
docker compose --profile load run --rm k6
```

`K6_BASE_URL` 기본값은 `http://host.docker.internal:8080`이며 로컬 HTTP 주소와 포트만 허용한다. `K6_TARGET_PATH`는 쿼리 없는 상대 경로를 받는다. 스크립트는 GET만 사용하고 redirect를 따라가지 않는다. 기본 범위는 1–50 VU, 1초–5분이다. 기본 성공 기준은 HTTP 200 검사 성공률 99% 초과, 요청 실패율 1% 미만, 응답 p95 500ms 미만이며 위반 시 k6가 실패 코드로 종료한다. 이는 로컬 시작값이며 운영 SLO가 아니다.

k6는 응답 본문을 버리고 URL을 지표 label에서 제외한다. 결과는 [k6의 Prometheus remote write 방식](https://grafana.com/docs/k6/latest/results-output/real-time/prometheus-remote-write/)으로 저장한다. 해당 출력 모듈은 experimental 상태이며, [Prometheus receiver 옵션](https://prometheus.io/docs/prometheus/latest/command-line/prometheus/)을 활성화해 수신한다. Grafana의 `k6 test` 필터로 실행을 선택한다. p95는 한 k6 프로세스의 관측값이며 여러 프로세스의 백분위수를 합산하지 않는다.

## 3. 확인 범위

컨테이너 healthcheck 통과, 백엔드 Flyway·Hibernate 검증 성공, Prometheus target `UP`, Grafana 데이터 표시, k6 종료 코드와 결과 수신을 각각 확인한다. 설정·부하 옵션만 검사하려면 [검증 안내](testing.md#docker-구성과-부하-옵션)를 따른다. 구성 검사 성공이 실제 기동이나 외부 연동 성공을 의미하지는 않는다.
