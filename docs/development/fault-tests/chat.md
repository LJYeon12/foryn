# 채팅 저장·복구 장애 검사

실제 MySQL·MongoDB replica set과 별도 JVM에서 저장·보상·재시작 복구를 확인한다. JDK 21, Python, Docker Linux 엔진과 Gradle 의존성이 필요하다. [검증 안내](../testing.md)의 선택 실행 검사이며 일반 `test`·`build`에 포함되지 않는다. 모든 명령은 저장소 루트에서 실행한다.

복구 상태와 운영 대응은 [채팅 저장·복구 절차](../../runbooks/chat-recovery.md)를 따른다.

## 실행과 판정

```powershell
python scripts/chat_fault.py
python scripts/chat_fault.py --execute --repetitions 100 --process-repetitions 1
```

첫 명령은 실행 계획만 출력한다. 두 번째는 이름·label·원본 container ID를 확인하는 일회성 MySQL·MongoDB replica set을 사용하고 종료 후 정리한다. 기존 Compose DB는 사용하지 않는다. 환경·소스 해시·원본 관측·검사 로그·독립 재계산 결과는 실행별 로컬 기록으로 남긴다.

서비스 호출 예외와 최종 자료 불일치는 별도로 센다. 저장 거부와 응답 유실은 실제 쓰기 전후의 검증용 예외이며, MySQL UPDATE 거부는 DB trigger다. SQL 커밋 응답 유실은 실제 JDBC commit 뒤 주입한다. JVM 종료는 별도 프로세스의 종료와 새 프로세스의 복구로 검사한다. 실제 네트워크 패킷 손실·primary 교체·운영 RPS를 측정한 결과로 사용하지 않는다.

반복 검사는 lease와 다음 시도 시각을 검증용 SQL로 앞당긴다. 복구 시간에는 운영 기본 300초 lease 대기와 backoff가 포함되지 않는다. 관측값을 운영 복구 시간 보장으로 사용하지 않는다.
