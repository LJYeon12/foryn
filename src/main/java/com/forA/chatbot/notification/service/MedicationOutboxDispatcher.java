package com.forA.chatbot.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.medications.domain.MedicationScheduleOutbox;
import com.forA.chatbot.medications.domain.MedicationScheduleOutbox.Status;
import com.forA.chatbot.medications.repository.MedicationBundleRepository;
import com.forA.chatbot.medications.repository.MedicationScheduleOutboxRepository;
import com.forA.chatbot.notification.client.CloudTasksClient;
import com.forA.chatbot.notification.dto.SendTaskPayloadDto;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class MedicationOutboxDispatcher {

  private static final int MAX_ATTEMPTS = 4;
  private static final int BATCH_SIZE = 100;
  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private final MedicationScheduleOutboxRepository outboxRepository;
  private final MedicationBundleRepository bundleRepository;
  private final CloudTasksClient cloudTasksClient;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;
  private final TransactionTemplate transaction;

  public MedicationOutboxDispatcher(MedicationScheduleOutboxRepository outboxRepository,
      MedicationBundleRepository bundleRepository, CloudTasksClient cloudTasksClient,
      ObjectMapper objectMapper, MeterRegistry meterRegistry, PlatformTransactionManager transactionManager) {
    this.outboxRepository = outboxRepository;
    this.bundleRepository = bundleRepository;
    this.cloudTasksClient = cloudTasksClient;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
    this.transaction = new TransactionTemplate(transactionManager);
    this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public void dispatchBundle(Long bundleId) {
    dispatchRunnable(bundleId);
  }

  public void recoverAndDispatch() {
    Instant now = Instant.now();
    Integer failed = transaction.execute(status -> outboxRepository.failExhaustedClaims(now, MAX_ATTEMPTS));
    Integer recovered = transaction.execute(status -> outboxRepository.recoverExpiredClaims(now, MAX_ATTEMPTS));
    if (failed != null && failed > 0) {
      meterRegistry.counter("chatbot.notification.outbox.total", "result", "failed").increment(failed);
      log.error("Medication outbox lease retry exhausted: count={}", failed);
    }
    if (recovered != null && recovered > 0) {
      meterRegistry.counter("chatbot.notification.outbox.total", "result", "recovered").increment(recovered);
    }
    dispatchRunnable(null);
  }

  public void retryFailed(Long jobId) {
    transaction.executeWithoutResult(status -> outboxRepository.retryFailed(jobId, Instant.now()));
    dispatchOne(jobId);
  }

  private void dispatchRunnable(Long bundleId) {
    for (Long id : outboxRepository.findRunnableIds(bundleId, Instant.now(), PageRequest.of(0, BATCH_SIZE))) {
      dispatchOne(id);
    }
  }

  public void dispatchOne(Long id) {
    String owner = UUID.randomUUID().toString();
    Instant now = Instant.now();
    MedicationScheduleOutbox job = transaction.execute(status -> {
      if (outboxRepository.claim(id, owner, now, now.plus(Duration.ofMinutes(5)), MAX_ATTEMPTS) == 0) {
        return null;
      }
      return outboxRepository.findById(id).orElse(null);
    });
    if (job == null) {
      return;
    }

    try {
      MedicationBundle bundle = bundleRepository.findById(job.getBundleId()).orElse(null);
      if (bundle == null || bundle.getScheduleVersion() != job.getScheduleVersion()
          || !bundle.isScheduledOn(job.getScheduledDate())) {
        finish(job, owner, Status.SUPERSEDED, null, null);
        return;
      }
      if (job.getScheduledDate().isBefore(LocalDate.now(KST))) {
        finish(job, owner, Status.EXPIRED, null, null);
        return;
      }

      String payload = objectMapper.writeValueAsString(new SendTaskPayloadDto(
          job.getBundleId(), job.getScheduledDate(), job.getScheduleVersion()));
      // 이 불변 작업 식별자는 삭제하거나 재사용하지 않는다. 확인 응답이 유실되어도 안전하게 재시도할 수 있다.
      cloudTasksClient.createTask(job.taskId(), "/internal/notifications/send/" + job.getBundleId(),
          payload, job.getScheduledAt());
      finish(job, owner, Status.COMPLETED, null, null);
    } catch (Exception e) {
      boolean retry = job.getAttempts() < MAX_ATTEMPTS;
      long minutes = switch (job.getAttempts()) {
        case 1 -> 1;
        case 2 -> 5;
        default -> 15;
      };
      // 길이를 제한한 예외 클래스명만 저장한다. SDK 페이로드, 자격증명, 예외 메시지는 저장하지 않는다.
      String error = e.getClass().getSimpleName();
      finish(job, owner, retry ? Status.RETRY_WAITING : Status.FAILED,
          retry ? Instant.now().plus(Duration.ofMinutes(minutes)) : null,
          error.substring(0, Math.min(100, error.length())));
      log.warn("Medication task registration failed: jobId={}, attempt={}, retry={}",
          id, job.getAttempts(), retry);
    }
  }

  private void finish(MedicationScheduleOutbox job, String owner, Status result,
      Instant nextAttemptAt, String error) {
    Integer updated = transaction.execute(status -> outboxRepository.finish(
        job.getId(), owner, result, nextAttemptAt, error));
    if (updated != null && updated > 0) {
      meterRegistry.counter("chatbot.notification.outbox.total", "result", result.name().toLowerCase(Locale.ROOT))
          .increment();
    }
  }
}
