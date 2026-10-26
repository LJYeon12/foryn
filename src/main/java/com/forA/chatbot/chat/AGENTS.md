# 채팅 변경 규칙

사용자 정서·건강 관련 답변은 제품 안전 경계다. 모델·저장·비용·안전의 변경을 함께 검토한다.

## 모델·프롬프트·비용

- 모델/API의 현행 값은 Spring AI 설정과 `ChatAiService`를 확인한다. 새 외부 호출은 timeout·retry·fallback·metric을 함께 정의한다. latency budget은 미확정이다.
- temperature·seed·structured output과 system prompt·behavioral skill 변경은 행동 변경이다. ADR, diff와 회귀 예시를 남긴다.
- 행동 데이터는 `src/main/resources/behavioral-skills.json`과 provider를 확인한다. 사용자 입력을 system/developer 지시로 승격하거나 system prompt에 직접 삽입하지 않는다.
- token/cost 계측 없이 새 모델 endpoint·긴 context·반복 호출을 추가하지 않는다. 반복에는 시도 상한과 backoff를 둔다.
- token budget 초과 시 요약·절단·명시 실패 중 하나를 선택하고 테스트한다. 캐시는 민감정보 저장 위험을 ADR로 검토한 뒤 도입한다.

## 저장·실패·취소

- MySQL은 프로필·복구 작업, MongoDB는 대화·사용자 쓰기 작업권을 보관한다. 변경 시 사용자 작업권과 MongoDB 트랜잭션을 유지한다.
- 프로필 변경은 임시 저장 → SQL 커밋 표시 → 확정 또는 보상 경계를 따른다. [복구 절차](../../../../../../../docs/runbooks/chat-recovery.md)를 확인한다.
- 새 대화·이어쓰기는 `ChatService`가 결정한다. 최근 24시간 조회는 시간 경계 테스트를 유지한다. 대화 일관성 변경은 저장량·PII·비용을 검토한다.
- JSON·DTO 응답은 저장 전 schema·enum을 검증한다. 잘못된 응답은 최대 1회 보정 후 안전하게 실패한다.
- OpenAI timeout·5xx는 최대 2회 재시도와 지수 backoff 후 일시 실패를 반환한다. 429는 backoff 또는 명시 실패로 처리한다. DB 실패는 중복 저장·세션 일관성을 검사한다.
- streaming 도입 전 연결 종료·취소 테스트와 부분 저장 정책을 추가한다. 검증 전 부분 응답을 신뢰 저장하지 않는다. 새 blocking 호출은 timeout·cancel 테스트가 있어야 한다.

## 안전·관측·모듈 경계

- 자해·타해·의학적 조언·위기는 안전 안내와 전문 도움 유도를 우선한다. 진단·처방·복용 변경을 지시하지 않는다. 잘못된 위험 감지에는 사용자가 다시 말할 수 있는 중립 문구를 제공한다.
- PII·건강정보·대화 원문을 로그·Sentry에 남기지 않는다. trace는 userId 해시·세션 ID까지 제한한다. 원문 trace 저장은 별도 승인이 필요하다.
- latency·token/cost·실패 지표는 cardinality를 제한한다. 에러율·잘못된 출력·요청당 비용을 회귀 신호로 확인한다.
- auth 접점은 인증된 사용자 ID·필요한 프로필 조회, 구독 권한·차감은 subscriptions Service로 제한한다. 알림을 추가하면 이벤트와 transaction phase를 명시한다.
