# 설정 변경 규칙

설정은 보안·외부 SDK·property 연결 경계다. 변경한 설정의 소비 모듈과 테스트를 함께 확인한다.

## 보안·프로필

- Security filter 순서·공개 경로 변경은 auth의 Controller·Security 테스트와 함께 검증한다. 내부 경로는 `InternalSecurityConfig`와 OIDC filter를 함께 확인한다.
- prod와 test 전용 property를 섞지 않는다. prod에서 test fallback을 허용하지 않는다.
- test는 외부 연동의 대역 값을 사용하고 외부 네트워크에 의존하지 않는다. local·prod의 실제 저장소·지표·로깅 설정은 실행 구성과 `docs/ARCHITECTURE.md`를 확인한다.
- JWT·OpenAI·Firebase·Apple·DB·Sentry 자격증명은 환경에서 주입한다. 경로·TTL·지표 노출·큐 이름 등 비밀이 아닌 값만 공유 설정에 둔다.
- 새 property는 테스트에 대역 값을 추가하고 누락·잘못된 값의 실패 메시지를 확인한다.

## 초기화·주입

- `@PostConstruct`의 장시간 네트워크 호출을 피한다. SDK 초기화는 파일·환경 확인 중심으로 유지한다.
- 필수 자격증명 누락·개인키 decode 실패는 기동 실패로 드러낸다. 선택 외부 연동의 장애가 전체 앱을 불필요하게 중단하지 않게 한다.
- warm-up은 health/readiness와 분리해 설계한다.
- 공통 ObjectMapper·FirebaseApp·RestTemplate은 Bean 또는 Client로 모은다. 외부 SDK 생성·주입은 루트의 계층 규칙을 따른다.
- Firebase 키 로딩, Cloud Tasks property, Sentry 이벤트 필터 변경은 각 Config와 소비 코드를 확인한다. Sentry 설정만으로 모든 PII가 제거된다고 가정하지 않는다.
- 설정 변경 시 관련 workflow·property·test를 함께 검토한다.
