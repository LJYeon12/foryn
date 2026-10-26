# 인증 변경 규칙

클라이언트 요청은 불신한다. 신원은 Apple 공개키/JWS 검증과 서버 DB 상태로 확인하고, 인가는 서버 DB·UserDetails·SecurityContext를 기준으로 처리한다.

## 토큰과 신뢰 경계

- 탈취·만료 토큰, 위조 Apple identity token과 refresh token replay를 가정한다. TLS 종단·DB 관리자 침해는 인프라 경계에서 다룬다.
- TTL은 `jwt.expiration_time`, refresh token 저장 상태는 `RefreshTokenRepository`를 확인한다. 배포 워크플로의 과거 주입값을 현재 값으로 가정하지 않는다.
- logout의 access token 차단, refresh token 제거와 기기 토큰 제거를 함께 유지한다. 다중 기기 정책 변경은 User·DeviceToken·RefreshToken 경계 테스트를 포함한다.
- 재발급은 기존 토큰과 저장 상태를 확인한다. blacklist 조회 누락을 회귀 테스트로 막는다.
- 사용자 식별은 `@AuthenticationPrincipal CustomUserDetails`를 따른다. 클라이언트 role·userId·provider 값만으로 권한을 부여하지 않는다.
- 인증 실패는 401 계열, 권한 실패는 403 계열로 구분한다. 시간 비교는 서버 시계 기준이며 clock skew 허용 변경은 ADR에 남긴다.

## 변경과 실패 처리

- JWT 알고리즘·claim 변경은 `JwtUtilTest`와 Controller·Service 테스트를 먼저 갱신한다. 직접 암호 알고리즘을 구현하지 않는다.
- Apple 키 조회 실패 시 캐시 TTL·재시도를 확인하고 일반 인증 실패로 응답한다. 보안 로그는 reason code·request path, Sentry는 민감값 없는 에러 코드로 제한한다.
- 로그인·refresh·logout·blacklist 변경은 추적 가능해야 한다. 토큰·JWS·개인키·email·name·providerUserId를 로그·Sentry·실값 fixture에 남기지 않는다.
- 기존 `UserRepository` 접점 외의 auth/user 접점은 Service로 제한한다. Security filter chain 변경은 `config/SecurityConfig`, Cloud Tasks OIDC 변경은 `OidcTokenFilter`와 internal endpoint를 함께 검사한다.
- 구현 후 별도 맥락에서 자가 검증한다. 비밀키·property 이름 변경 PR은 기능 변경과 분리한다. 위협 모델·감사 저장 구조 변경은 ADR에 남긴다.
- prod 인증 우회나 임시 로그인 확장을 추가하지 않는다.
