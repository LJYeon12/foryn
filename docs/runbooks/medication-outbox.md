이 문서는 복약 예약 등록 지연, Outbox 실패 종료 또는 오래된 처리권이 발생했을 때 본다.

# 복약 예약 Outbox 운영 절차

## 실행 계약

- 구현: `MedicationScheduleOutboxWriter`, `MedicationOutboxDispatcher`, `InternalNotificationController`.
- 스키마: `V13__Add_medication_schedule_outbox.sql`. 기존 migration은 수정하지 않는다.
- 계획 저장과 Outbox 저장은 같은 트랜잭션이다. 저장 응답은 외부 등록 완료나 기기 도달 완료가 아니다.
- `POST /internal/notifications/schedule-daily`의 복약 처리는 해당 날짜의 예약 의도를 저장한다. 일기 배치의 기존 동작은 유지한다.
- 커밋 이후 즉시 등록을 시도하며, 이벤트 유실은 별도 복구 요청으로 처리한다.
- `POST /internal/notifications/medication-outbox/recover`는 5분 지난 처리권을 회수하고 실행 가능한 작업을 최대 100개 처리한다. 필요하면 반복 호출한다.
- 최초 포함 최대 4번 등록한다. 실패 간격은 1분·5분·15분이며, 시도 소진은 `FAILED`로 보존한다.
- Outbox `COMPLETED`는 Cloud Tasks 등록 성공이다. 실제 발송 결과는 `notification_send_log`와 FCM 지표에서 확인한다.

## 먼저 확인할 항목

운영 연결 전 실제 프로젝트·리전·Cloud Run 대상·queue를 운영 문서에 확정한다. 현재 개발 복사본에는 연결된 운영 환경이 없다. 다음은 조회 전용 SQL이며 개인정보·토큰 원문을 조회하지 않는다.

```sql
SELECT status, COUNT(*) AS jobs, MIN(next_attempt_at) AS oldest_next_attempt
FROM medication_schedule_outbox
GROUP BY status;

SELECT id, bundle_id, scheduled_date, schedule_version, status,
       attempts, next_attempt_at, lock_until, last_error
FROM medication_schedule_outbox
WHERE status IN ('PENDING', 'PROCESSING', 'RETRY_WAITING', 'FAILED')
ORDER BY next_attempt_at, id
LIMIT 100;
```

`PROCESSING`이 5분을 넘었는지, `FAILED`가 늘어나는지, 예정 시각이 지났지만 같은 서울 날짜인지 확인한다. 실제 Cloud Tasks queue의 등록·실행 오류와 내부 OIDC의 audience·실행 계정도 확인한다. Micrometer counter `chatbot.notification.outbox.total`의 `failed`, `retry_waiting`, `recovered`, `completed` 결과를 관측 대상으로 연결한다. 알람이 자동으로 구성되어 있다는 의미는 아니다.

## 주기 복구 연결

Cloud Run에서는 내부 scheduler가 계속 실행된다고 가정하지 않는다. Cloud Scheduler 또는 동등한 인증된 외부 호출로 복구 요청을 연결한다. 이 호출은 발송할 복약을 직접 검색해 보내지 않고 미완료 예약 등록만 처리한다.

| 설정 | 값·확인 기준 |
| --- | --- |
| 프로젝트·리전 | 재개 대상 운영 문서에서 확정한 값 |
| Cloud Run 대상 | `cloud-tasks.target-base-url`에 대응하는 호환 애플리케이션 |
| 경로·방식 | `POST /internal/notifications/medication-outbox/recover` |
| 권장 시작 주기 | 매분. 처리 지연과 작업량을 계측한 뒤 조정 |
| 인증 | OIDC. audience는 `cloud-tasks.target-base-url`, 계정은 `cloud-tasks.task-execution-sa-email`과 일치 |
| 호출 권한 | 대상 Cloud Run 호출 권한과 기존 내부 OIDC 검사 모두 충족 |
| 중복 호출 | DB 선점으로 같은 작업의 동시 처리를 제한 |
| 중단·rollback | 주기 호출을 일시 정지하고 아래 배포·보상 절차 적용 |

외부 리소스를 자동 생성하거나 변경하는 명령은 이 소스에 포함하지 않는다. 일별 예약 배치 호출도 별도 연결을 유지해야 한다.

## 실패 종료 작업 재시도

1. 위 조회로 대상 `id`·계획·날짜·상태를 먼저 확인한다. 잘못된 계획은 앱의 정상 수정 절차로 보정한다.
2. 등록망·자격·OIDC 문제를 해결한 뒤 인증된 `POST /internal/notifications/medication-outbox/{jobId}/retry`를 호출한다.
3. `FAILED` 작업만 시도 횟수를 초기화한다. 다시 처리할 때 삭제·버전 변경은 `SUPERSEDED`, 지난 날짜는 `EXPIRED`로 종료한다.
4. `COMPLETED`와 발송 기록을 각각 확인한다. 반복 실패는 자동 무한 재시도로 덮지 않는다.

예약을 수동 삭제한 뒤 같은 Task ID로 재생성하지 않는다. 같은 의도는 같은 불변 ID를 사용하므로 등록 응답 유실 후 재시도가 가능하고, 다른 계획 버전은 다른 ID를 사용한다. 이전 예약은 남아 있어도 발송 전 버전 검사로 무시한다.

## 배포·보상

1. 읽기 전용으로 schema history, 대기 작업, 활성 queue와 실제 대상 revision을 확인한다. 운영 연결이 없는 초기 환경에서는 V13 적용과 새 코드 검증 후 연결한다.
2. 기존 운영에서 변경한다면 내부 발송 대상과 계획 쓰기 경로의 구·신버전 혼용을 피하도록 호환 배포 단계를 먼저 확정한다. 기존 형식의 Task는 버전 0인 계획에만 허용한다. Cloud Run revision/canary와 중단·복귀 가능한 단계로 배포하며 즉시 전체 트래픽 전환을 하지 않는다.
3. 문제 발생 시 일별 등록·Outbox 복구 호출과 관련 queue 실행을 일시 정지하고 작업 상태를 보존한다. 이미 진행한 FCM 호출은 취소되지 않을 수 있다.
4. Outbox·버전을 이해하는 검증된 호환 revision으로 rollback하거나 수정 버전으로 전진한다. 버전을 무시하는 구형 코드로 단독 rollback하거나 계획 버전을 0으로 초기화하지 않는다.
5. V13은 추가 스키마이므로 테이블·버전 열을 즉흥적으로 삭제하지 않는다. 스키마 보상이 필요하면 대기 작업·queue와의 정합성을 확인한 뒤 별도 Flyway migration으로 수행한다.

기존 발송 중복 키는 알림 종류·계획·날짜·기기다. 설정 변경은 이미 발송한 알림의 재발송 권한이 아니다. FCM 성공과 DB 완료 기록 사이 중단의 중복 가능성은 남으며 exactly-once 기기 도달을 보장하지 않는다.
