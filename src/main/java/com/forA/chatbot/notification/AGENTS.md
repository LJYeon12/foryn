# 알림 변경 규칙

FCM·Cloud Tasks·예약·발송 로그를 다룬다. 짧은 지연보다 누락 방지를 우선하되 재시도로 중복이 폭주하지 않게 한다.

## 예약·중복·인증

- 전달은 재시도와 중복을 허용하는 구조다. 정확히 한 번 수신을 보장한다고 가정하지 않는다.
- 발송 dedup은 `(notification_type, target_id, scheduled_date, device_token_id)` unique constraint를 따른다. 날짜 단위 경계 변경은 migration과 테스트가 필요하다.
- task는 대상·날짜·종류로 재구성할 수 있어야 한다. lock·claim 변경은 동시성 테스트를 먼저 추가한다.
- 복약 변경은 medications가 소유한다. SQL 트랜잭션의 예약 의도 저장과 커밋 후 외부 등록을 구분한다. [Outbox 절차](../../../../../../../docs/runbooks/medication-outbox.md)를 확인한다.
- 상위 트랜잭션 rollback 시 예약 의도가 남거나 외부 push가 확정되지 않게 한다.
- Cloud Tasks internal endpoint의 OIDC filter를 우회하지 않는다. 검증 실패는 401/403으로 거절하고 payload를 처리하지 않는다.
- auth/user 접점은 사용자 ID·기기 토큰 소유권 확인으로 제한한다.

## 실패·부하·민감정보

- FCM invalid/unregistered token은 비활성화 또는 정리 대상으로 처리한다. 일시 오류는 최대 3회 재시도·backoff 후 실패 상태와 민감값 없는 오류를 남긴다.
- Cloud Tasks 등록 실패는 SQL 예약 의도의 rollback·재시도 경계를 명시한다. 무한 재시도하지 않는다.
- 대량 발송은 큐·FCM quota를 함께 확인한다. polling의 전체 scan·N+1을 피하고 실행 주기는 Scheduler와 설정에서 확인한다.
- payload에 복약 상세·토큰 원문·PII·내부 stack을 넣지 않는다. 크기 초과 시 title/body를 줄이고 data 확장을 제한한다.
- locale/timezone 정책은 미확정이다. 서버 기본 timezone에 기대지 말고 명시 필드를 우선한다.
- 성공·실패·중복·claim 실패를 metric 또는 log status로 확인한다. device token과 민감 payload를 로그·Sentry에 남기지 않는다.
