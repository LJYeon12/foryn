# 로컬 실행

JDK 21과 Docker Desktop의 Linux 엔진이 필요하다. 모든 명령은 저장소 루트에서 실행한다. 백엔드는 호스트/IDE에서 실행하고, MySQL·MongoDB·Prometheus·Grafana는 `compose.yaml`로 실행한다. 외부 연동에 필요한 환경변수와 인증서는 직접 준비한다.

실행 환경과 기존 외부 연동의 역할은 [아키텍처](../ARCHITECTURE.md#실행-환경과-외부-연동)를 따른다. 지표와 k6는 [관측과 부하 확인](observability.md), 검사 명령은 [검증 안내](testing.md)를 따른다.

## 구성

| 서비스 | 이미지 | 호스트 접근 | 저장 |
| --- | --- | --- | --- |
| MySQL (`db`) | `mysql:8.4` | `localhost:3306` | `mysql-data` 볼륨 |
| MongoDB (`mongo`) | `mongo:8.0`, 단일 노드 replica set `rs0` | `localhost:27017` | `mongo-data`, `mongo-config` 볼륨 |
| Prometheus | `prom/prometheus:v3.14.0` | [localhost:9090](http://localhost:9090) | `prometheus-data`, 보관 7일 또는 2GB |
| Grafana | `grafana/grafana:13.2.3` | [localhost:3000](http://localhost:3000) | `grafana-data` 볼륨 |
| k6 | `grafana/k6:2.3.0` | 필요할 때 실행 | 결과를 Prometheus에 전송 |

모든 공개 포트는 `127.0.0.1`에 바인딩한다. Compose 프로젝트 이름은 `foryn-local`이다. DB 이미지는 minor 태그를 사용하므로 새로 pull하면 해당 계열의 패치 버전이 바뀔 수 있다.

~~~mermaid
flowchart LR
    IDE[호스트 Spring Boot] --> MySQL[MySQL]
    IDE --> Mongo[MongoDB]
    Prom[Prometheus] -->|actuator 지표 수집| IDE
    K6[k6 수동 실행] -->|로컬 GET 요청| IDE
    K6 -->|remote write| Prom
    Grafana[Grafana] -->|지표 조회| Prom
~~~

## 1. 환경변수와 컨테이너 실행

Docker Desktop의 Linux 엔진을 실행한다. 저장소 루트에서 아래 명령을 실행한다. 기존 `.env`가 있으면 유지하고 예제에서 필요한 키만 추가한다.

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
```

`.env`에서 `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `MONGO_INITDB_ROOT_PASSWORD`, `GRAFANA_ADMIN_PASSWORD`를 채운다. 값이 비어 있으면 Compose가 시작 전에 실패한다. 로컬 MongoDB 비밀번호는 연결 URI에 그대로 삽입되므로 영숫자·`-`·`_` 조합을 사용한다. URI 예약문자를 사용한다면 애플리케이션 연결 URI를 별도로 percent encoding해 주입해야 한다.

DB 이름·사용자명은 예제와 애플리케이션 양쪽에서 일치시킨다. 기존 외부 서비스의 자격증명은 기존 환경변수/비밀 주입 경로로 제공한다. `.env`는 Git에서 제외한다. Compose 설정 확인 시 `--quiet`을 사용하면 해석된 비밀번호를 터미널에 출력하지 않는다.

```powershell
docker compose config --quiet
docker compose up -d --wait db mongo prometheus grafana
docker compose ps
```

DB 사용자와 비밀번호는 빈 볼륨의 첫 초기화에 적용된다. 이후 `.env`만 바꿔도 기존 DB 계정 비밀번호가 변경되지는 않는다. Grafana 관리자 초기 비밀번호도 기존 볼륨에서 자동 재설정되지 않는다.

## 2. IDE 또는 Gradle에서 백엔드 실행

### Apple 공개 루트 인증서 준비

새로 복제한 저장소에는 `src/main/resources/certs/`의 인증서가 포함되지 않는다. 현재 `AppStoreJwsValidator`는 앱 시작 시 `classpath:certs/AppleRootCA-G3.cer`를 읽는다. 파일이 없거나 X.509 인증서로 읽을 수 없으면 앱 시작이 실패한다. 다른 외부 연동 환경변수도 별도로 필요하다.

1. [Apple 공식 PKI 페이지](https://www.apple.com/certificateauthority/)의 **Apple Root Certificates**에서 **Apple Root CA - G3 Root**를 내려받는다.
2. 내려받은 공개 인증서를 로컬의 `src/main/resources/certs/AppleRootCA-G3.cer`에 직접 배치한다. 기존 파일이 있으면 공식 배포본인지 확인한다.
3. 아래 명령으로 파일 존재 여부를 확인한다. `False`이면 경로와 파일 이름을 확인한다.

```powershell
Test-Path -LiteralPath src/main/resources/certs/AppleRootCA-G3.cer -PathType Leaf
```

파일 존재 확인은 인증서의 신뢰성이나 형식 검증을 대신하지 않는다. 공식 페이지에서 받은 파일을 사용한다. 이 공개 루트 인증서는 서명 검증용이며, Apple 로그인·App Store용 개인 키(`.p8`)와 다르다. 개인 키를 이 파일 대신 넣지 않는다.

인증서 폴더는 계속 Git에서 제외한다. 다른 개발 환경에서도 실행 전에 같은 준비가 필요하다. 현재 Gradle 리소스 복사는 이 `.cer` 파일을 포함하므로 실행 JAR을 만들기 전에도 준비해야 한다. 인증서 원문과 개인 키는 문서·로그·Git에 넣지 않는다.

### 백엔드 실행 인자

작업 디렉터리를 저장소 루트로 설정하고 기존 외부 연동 환경변수를 준비한다. IDE의 Program arguments에 다음을 넣는다.

```text
--spring.profiles.active=local --spring.config.additional-location=file:./infra/local/application-observability.properties
```

또는 PowerShell에서 실행한다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local --spring.config.additional-location=file:./infra/local/application-observability.properties"
```

`infra/local/application-observability.properties`는 기본 DB 호스트 `db`·`mongo`를 호스트 실행용 `localhost`로 덮어쓰고, Actuator의 `health`·`prometheus` 노출과 HTTP 지연 histogram을 활성화한다. 스키마는 Flyway migration으로 적용하고 Hibernate는 `validate`만 실행한다. SQL 출력은 끈다. 외부 연동에 필요한 기존 인증·JWT·생성형 응답 등의 설정은 별도로 필요하다.

MongoDB는 인증을 유지한 단일 노드 replica set으로 시작한다. 내부 인증 키는 컨테이너 시작 시 무작위로 생성해 `mongo-config` 볼륨에만 보관한다. 시작 스크립트는 초기 사용자 생성이 끝난 뒤 replica set을 초기화하고, 재시작 시 기존 설정을 사용한다. healthcheck는 primary 여부를 읽기만 한다. 호스트 연결 URI에는 `authSource=admin&replicaSet=rs0&directConnection=true`를 사용한다. 이 구성은 다중 문서 트랜잭션을 검증하는 로컬 구성이며 노드 장애 시 고가용성 구성은 아니다.

기존 볼륨은 삭제하지 않는다. 이미 다른 replica set 이름·사용자·비밀번호로 만든 볼륨은 현재 설정과 대조해야 한다. root 비밀번호를 환경변수에서 바꾼 것만으로 기존 DB 사용자의 비밀번호가 바뀌지 않는다. 채팅 스키마·인덱스는 Flyway V14·V15로 준비한다. 상세 상태와 격리된 장애 검사는 [채팅 저장·복구 절차](../runbooks/chat-recovery.md)를 따른다.

`local` 프로필의 기존 보안 규칙이 Prometheus 수집 요청을 허용한다. 프로필을 빼면 컨테이너에서 오는 요청이 인증 대상이 될 수 있다. 프로필과 추가 설정은 위 실행 인자로 로컬 실행에만 적용한다.

## 3. 중지

```powershell
docker compose down
```

위 명령은 데이터 볼륨을 보존한다. 다시 같은 프로젝트로 `up`하면 저장 데이터를 재사용한다.
