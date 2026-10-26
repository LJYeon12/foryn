# 구독 변경 규칙

Apple App Store 검증·서버 알림·구독 상태는 금전과 권한 경계다. 금액·권한·상태 전이 변경은 다른 기능 변경과 분리한다.

## 검증·상태·중복

- 사용자별 단일 구독과 `original_transaction_id`의 유일성은 DB constraint로 유지한다. transaction/original transaction ID로 중복 권한 부여를 막는다.
- 클라이언트 productId·status·expiresAt·isTrial·transactionId·originalTransactionId를 검증 없이 신뢰하지 않는다. Apple API/JWS 검증 결과와 서버 DB가 기준이다.
- 상태·알림 해석은 현재 enum·Service를 확인한다. 허용 전이 변경은 감사 기록과 테스트를 포함한다.
- 만료·갱신·취소는 검증한 payload 시간과 서버 시간을 비교한다. 오래된 webhook이 최신 상태를 덮지 않게 하고 재전송은 멱등 처리한다.
- JWS 서명 실패는 저장·상태 변경 없이 거절하고 민감값 없는 보안 로그를 남긴다.
- 외부 검증 성공 후 DB에 반영한다. DB 실패 시 보상 가능성을 추적한다.

## 실패·감사·미확정 정책

- Apple timeout·5xx는 최대 2회 재시도 후 pending 또는 실패로 처리한다. 권한을 낙관적으로 부여하지 않는다.
- sandbox/prod endpoint는 Client·property를 분리하고 테스트로 고정한다.
- 구현 후 다른 맥락에서 테스트·자가 리뷰를 한 번 더 수행한다. 인증서·키·bundle ID 변경은 config·workflow·문서를 함께 검토한다.
- 검증 성공·실패, webhook, 상태 변경·만료는 추적 가능해야 한다. 거래 ID 전체·signed payload·추가 식별자는 로그·Sentry에 남기지 않는다.
- 환불·proration·환율, 결제 로그 보관 기간은 미확정이다. 정책 변경은 ADR과 테스트를 먼저 추가하고 삭제·익명화 정책 없이 원문 저장을 늘리지 않는다.
- 관리자 보정은 runbook과 감사 절차를 먼저 마련한다.
