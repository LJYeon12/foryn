package com.forA.chatbot.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.enums.ProviderType;
import com.forA.chatbot.medications.converter.MedicationConverter;
import com.forA.chatbot.medications.domain.MedicationScheduleOutbox;
import com.forA.chatbot.medications.domain.MedicationScheduleOutbox.Status;
import com.forA.chatbot.medications.dto.MedicationRequestDto;
import com.forA.chatbot.medications.dto.MedicationUpdateRequestDto;
import com.forA.chatbot.medications.repository.MedicationBundleRepository;
import com.forA.chatbot.medications.repository.MedicationScheduleOutboxRepository;
import com.forA.chatbot.medications.service.MedicationLogSaver;
import com.forA.chatbot.medications.service.MedicationScheduleOutboxWriter;
import com.forA.chatbot.medications.service.MedicationService;
import com.forA.chatbot.medications.service.MedicationStreakService;
import com.forA.chatbot.notification.client.CloudTasksClient;
import com.forA.chatbot.user.domain.User;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({MedicationService.class, MedicationConverter.class, MedicationScheduleOutboxWriter.class,
    MedicationOutboxDispatcher.class, MedicationOutboxIntegrationTest.Configuration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MedicationOutboxIntegrationTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  @Autowired private MedicationService medicationService;
  @Autowired private MedicationScheduleOutboxWriter writer;
  @Autowired private MedicationOutboxDispatcher dispatcher;
  @Autowired private MedicationBundleRepository bundleRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoSpyBean private MedicationScheduleOutboxRepository outboxRepository;
  @MockitoBean private MedicationStreakService streakService;
  @MockitoBean private MedicationLogSaver logSaver;
  @MockitoBean private CloudTasksClient cloudTasksClient;

  private Long userId;

  @BeforeEach
  void createUser() {
    reset(cloudTasksClient);
    outboxRepository.deleteAllInBatch();
    userId = userRepository.saveAndFlush(User.builder()
        .email(UUID.randomUUID() + "@example.com")
        .providerUserId(UUID.randomUUID().toString())
        .providerType(ProviderType.APPLE)
        .fullName("Outbox Test")
        .build()).getId();
  }

  @Test
  @DisplayName("계획과 Outbox는 함께 커밋되고 메모리 이벤트를 잃어도 작업이 남는다")
  void createPlan_persistsOutboxWithoutEventConsumer() {
    Long bundleId = createPlan();

    MedicationScheduleOutbox job = outboxRepository
        .findByBundleIdAndScheduledDateAndScheduleVersion(bundleId, LocalDate.now(KST), 0L)
        .orElseThrow();
    assertThat(bundleRepository.existsById(bundleId)).isTrue();
    assertThat(job.getStatus()).isEqualTo(Status.EXPIRED);
    verifyNoInteractions(cloudTasksClient);
  }

  @Test
  @DisplayName("상위 트랜잭션이 실패하면 복약 계획과 Outbox가 함께 롤백된다")
  void rollback_removesPlanAndOutbox() {
    long plansBefore = bundleRepository.count();
    long jobsBefore = outboxRepository.count();

    assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
      createPlan();
      throw new IllegalStateException("rollback");
    })).isInstanceOf(IllegalStateException.class);

    assertThat(bundleRepository.count()).isEqualTo(plansBefore);
    assertThat(outboxRepository.count()).isEqualTo(jobsBefore);
  }

  @Test
  @DisplayName("Outbox 저장 실패 시 복약 계획만 저장되지 않는다")
  void outboxWriteFailure_rollsBackPlan() {
    long plansBefore = bundleRepository.count();
    willThrow(new IllegalStateException("outbox write failed"))
        .given(outboxRepository).saveAndFlush(any(MedicationScheduleOutbox.class));

    assertThatThrownBy(this::createPlan).isInstanceOf(IllegalStateException.class);

    assertThat(bundleRepository.count()).isEqualTo(plansBefore);
  }

  @Test
  @DisplayName("계획 변경 버전은 서로 다른 Outbox와 Task ID를 만든다")
  void updatePlan_usesNewVersionAndTaskIdentity() {
    Long bundleId = createPlan();
    MedicationScheduleOutbox first = tomorrowJob(bundleId);
    MedicationUpdateRequestDto update = MedicationUpdateRequestDto.builder().takeTime("00:01").build();

    medicationService.updateMedicationPlan(userId, bundleId, update);
    MedicationScheduleOutbox second = tomorrowJob(bundleId);

    assertThat(second.getScheduleVersion()).isGreaterThan(first.getScheduleVersion());
    assertThat(second.taskId()).isNotEqualTo(first.taskId());
    dispatcher.dispatchOne(first.getId());
    assertThat(reload(first).getStatus()).isEqualTo(Status.SUPERSEDED);
    verifyNoInteractions(cloudTasksClient);
  }

  @Test
  @DisplayName("계획 삭제 후 남아 있는 Outbox는 예약하지 않는다")
  void deletedPlan_doesNotEnqueue() {
    Long bundleId = createPlan();
    MedicationScheduleOutbox job = tomorrowJob(bundleId);

    medicationService.deleteMedicationPlan(userId, bundleId);
    dispatcher.dispatchOne(job.getId());

    assertThat(reload(job).getStatus()).isEqualTo(Status.SUPERSEDED);
    verifyNoInteractions(cloudTasksClient);
  }

  @Test
  @DisplayName("일별 배치가 동시에 재실행되어도 같은 버전의 Outbox는 하나다")
  void concurrentDailyRecording_isIdempotent() throws Exception {
    Long bundleId = createPlan();
    LocalDate date = LocalDate.now(KST).plusDays(1);
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<?> first = executor.submit(() -> recordAfter(start, bundleId, date));
      Future<?> second = executor.submit(() -> recordAfter(start, bundleId, date));
      start.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    }

    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM medication_schedule_outbox WHERE bundle_id = ? AND scheduled_date = ?",
        Integer.class, bundleId, date);
    assertThat(count).isEqualTo(1);
  }

  @Test
  @DisplayName("Cloud Tasks 등록은 DB 트랜잭션 밖에서 수행하고 완료를 기록한다")
  void dispatch_doesNotHoldTransactionDuringNetworkCall() {
    MedicationScheduleOutbox job = tomorrowJob(createPlan());
    org.mockito.Mockito.doAnswer(invocation -> {
      assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
      return null;
    }).when(cloudTasksClient).createTask(any(), any(), any(), any());

    dispatcher.dispatchOne(job.getId());

    assertThat(reload(job).getStatus()).isEqualTo(Status.COMPLETED);
    verify(cloudTasksClient).createTask(org.mockito.ArgumentMatchers.eq(job.taskId()),
        any(), any(), org.mockito.ArgumentMatchers.eq(job.getScheduledAt()));
  }

  @Test
  @DisplayName("등록 실패는 DB에 재시도로 남고 같은 작업 ID로 재등록된다")
  void failedEnqueue_isRetriedWithSameIdentity() {
    MedicationScheduleOutbox job = tomorrowJob(createPlan());
    willThrow(new IllegalStateException("sensitive payload must not be stored"))
        .willDoNothing().given(cloudTasksClient).createTask(any(), any(), any(), any());

    dispatcher.dispatchOne(job.getId());
    MedicationScheduleOutbox failed = reload(job);
    assertThat(failed.getStatus()).isEqualTo(Status.RETRY_WAITING);
    assertThat(failed.getNextAttemptAt()).isAfter(Instant.now());
    assertThat(failed.getLastError()).isEqualTo("IllegalStateException");
    dispatcher.dispatchOne(job.getId());
    verify(cloudTasksClient, times(1)).createTask(any(), any(), any(), any());

    makeRetryDue(job);
    dispatcher.dispatchOne(job.getId());

    ArgumentCaptor<String> ids = ArgumentCaptor.forClass(String.class);
    verify(cloudTasksClient, times(2)).createTask(ids.capture(), any(), any(), any());
    assertThat(ids.getAllValues()).containsExactly(job.taskId(), job.taskId());
    assertThat(reload(job).getStatus()).isEqualTo(Status.COMPLETED);
  }

  @Test
  @DisplayName("Cloud Tasks 장애가 반복되어도 무한 재시도하지 않고 FAILED로 남긴다")
  void retriesAreBounded_andFailedWorkIsRetained() {
    MedicationScheduleOutbox job = tomorrowJob(createPlan());
    willThrow(new IllegalStateException("unavailable"))
        .given(cloudTasksClient).createTask(any(), any(), any(), any());

    for (int attempt = 0; attempt < 4; attempt++) {
      makeRetryDue(job);
      dispatcher.dispatchOne(job.getId());
    }
    dispatcher.dispatchOne(job.getId());

    assertThat(reload(job).getStatus()).isEqualTo(Status.FAILED);
    assertThat(reload(job).getAttempts()).isEqualTo(4);
    verify(cloudTasksClient, times(4)).createTask(any(), any(), any(), any());
  }

  @Test
  @DisplayName("처리 중 종료된 Worker의 만료된 처리권을 회수한다")
  void recovery_reclaimsExpiredLease() {
    MedicationScheduleOutbox job = tomorrowJob(createPlan());
    transaction().executeWithoutResult(status -> assertThat(outboxRepository.claim(
        job.getId(), "dead-worker", Instant.now(),
        Instant.now().minusSeconds(60), 4)).isEqualTo(1));

    dispatcher.recoverAndDispatch();

    assertThat(reload(job).getStatus()).isEqualTo(Status.COMPLETED);
  }

  @Test
  @DisplayName("실패 종료한 작업은 명시적 재시도로 복구할 수 있다")
  void explicitRetry_requeuesFailedWork() {
    MedicationScheduleOutbox job = tomorrowJob(createPlan());
    jdbcTemplate.update("UPDATE medication_schedule_outbox SET status = 'FAILED', attempts = 4 WHERE id = ?",
        job.getId());

    dispatcher.retryFailed(job.getId());

    assertThat(reload(job).getStatus()).isEqualTo(Status.COMPLETED);
    assertThat(reload(job).getAttempts()).isEqualTo(1);
  }

  @Test
  @DisplayName("마지막 시도 중 Worker가 종료되어도 PROCESSING에 영구 방치하지 않는다")
  void exhaustedLease_isMarkedFailed() {
    MedicationScheduleOutbox job = tomorrowJob(createPlan());
    jdbcTemplate.update("""
        UPDATE medication_schedule_outbox
        SET status = 'PROCESSING', attempts = 4, lock_owner = 'dead-worker', lock_until = ?
        WHERE id = ?
        """, java.sql.Timestamp.from(Instant.now().minusSeconds(60)), job.getId());

    dispatcher.recoverAndDispatch();

    assertThat(reload(job).getStatus()).isEqualTo(Status.FAILED);
    verifyNoInteractions(cloudTasksClient);
  }

  @Test
  @DisplayName("전날 작업을 복구해도 오늘의 복약 알림으로 보내지 않는다")
  void recovery_expiresPreviousDay() {
    MedicationScheduleOutbox job = tomorrowJob(createPlan());
    jdbcTemplate.update("UPDATE medication_schedule_outbox SET scheduled_date = ? WHERE id = ?",
        LocalDate.now(KST).minusDays(1), job.getId());

    dispatcher.dispatchOne(job.getId());

    assertThat(reload(job).getStatus()).isEqualTo(Status.EXPIRED);
    verifyNoInteractions(cloudTasksClient);
  }

  @Test
  @DisplayName("복구 시점에 예약 시각이 지나도 같은 날짜의 유효 작업은 등록한다")
  void recovery_registersLateWorkWithinSameDate() {
    MedicationScheduleOutbox job = tomorrowJob(createPlan());
    jdbcTemplate.update("DELETE FROM medication_schedule_outbox WHERE bundle_id = ? AND scheduled_date = ?",
        job.getBundleId(), LocalDate.now(KST));
    jdbcTemplate.update("""
        UPDATE medication_schedule_outbox SET scheduled_date = ?, scheduled_at = ? WHERE id = ?
        """, LocalDate.now(KST), java.sql.Timestamp.from(Instant.now().minusSeconds(60)), job.getId());

    dispatcher.dispatchOne(job.getId());

    assertThat(reload(job).getStatus()).isEqualTo(Status.COMPLETED);
    verify(cloudTasksClient).createTask(any(), any(), any(), any());
  }

  @Test
  @DisplayName("동시 선점은 한 Worker에게만 처리권을 준다")
  void concurrentClaim_hasOneWinner() throws Exception {
    MedicationScheduleOutbox job = tomorrowJob(createPlan());
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<Integer> first = executor.submit(() -> claimAfter(start, job.getId()));
      Future<Integer> second = executor.submit(() -> claimAfter(start, job.getId()));
      start.countDown();
      assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("처리권을 잃은 Worker가 새 Worker의 완료 상태를 덮어쓸 수 없다")
  void expiredOwner_cannotFinishNewOwnersWork() {
    MedicationScheduleOutbox job = tomorrowJob(createPlan());
    Instant now = Instant.now();
    transaction().executeWithoutResult(status -> outboxRepository.claim(
        job.getId(), "old-owner", now, now.plusSeconds(60), 4));
    jdbcTemplate.update("UPDATE medication_schedule_outbox SET lock_owner = ? WHERE id = ?",
        "new-owner", job.getId());

    Integer updated = transaction().execute(status -> outboxRepository.finish(
        job.getId(), "old-owner", Status.COMPLETED, null, null));

    assertThat(updated).isZero();
    assertThat(reload(job).getStatus()).isEqualTo(Status.PROCESSING);
    assertThat(reload(job).getLockOwner()).isEqualTo("new-owner");
  }

  private Long createPlan() {
    return medicationService.createMedicationPlan(userId, MedicationRequestDto.builder()
        .name("plan-" + UUID.randomUUID())
        .typeTags(List.of("SUPPLEMENT"))
        .takeDays(List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"))
        .takeTime("00:00")
        .build()).getId();
  }

  private MedicationScheduleOutbox tomorrowJob(Long bundleId) {
    LocalDate date = LocalDate.now(KST).plusDays(1);
    writer.recordDaily(bundleId, date);
    long version = bundleRepository.findById(bundleId).orElseThrow().getScheduleVersion();
    return outboxRepository.findByBundleIdAndScheduledDateAndScheduleVersion(bundleId, date, version)
        .orElseThrow();
  }

  private MedicationScheduleOutbox reload(MedicationScheduleOutbox job) {
    return outboxRepository.findById(job.getId()).orElseThrow();
  }

  private void makeRetryDue(MedicationScheduleOutbox job) {
    jdbcTemplate.update("UPDATE medication_schedule_outbox SET next_attempt_at = ? WHERE id = ?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)), job.getId());
  }

  private void recordAfter(CountDownLatch start, Long bundleId, LocalDate date) {
    await(start);
    writer.recordDaily(bundleId, date);
  }

  private Integer claimAfter(CountDownLatch start, Long jobId) {
    await(start);
    Instant now = Instant.now();
    return transaction().execute(status -> outboxRepository.claim(
        jobId, UUID.randomUUID().toString(), now, now.plusSeconds(60), 4));
  }

  private void await(CountDownLatch start) {
    try {
      assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private TransactionTemplate transaction() {
    return new TransactionTemplate(transactionManager);
  }

  @TestConfiguration
  static class Configuration {
    @Bean ObjectMapper objectMapper() {
      return new ObjectMapper().findAndRegisterModules();
    }

    @Bean MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
