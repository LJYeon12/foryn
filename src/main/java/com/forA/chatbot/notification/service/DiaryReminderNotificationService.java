package com.forA.chatbot.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forA.chatbot.diary.domain.DiaryReminder;
import com.forA.chatbot.diary.domain.DiaryReminderRescheduleJob;
import com.forA.chatbot.diary.domain.enums.DiaryReminderRescheduleJobStatus;
import com.forA.chatbot.diary.event.DiaryReminderRescheduleRequestedEvent;
import com.forA.chatbot.diary.repository.DiaryReminderRepository;
import com.forA.chatbot.diary.repository.DiaryReminderRescheduleJobRepository;
import com.forA.chatbot.notification.client.CloudTasksClient;
import com.forA.chatbot.notification.converter.NotificationConverter;
import com.forA.chatbot.notification.domain.DeviceToken;
import com.forA.chatbot.notification.domain.NotificationType;
import com.forA.chatbot.notification.dto.DiaryReminderSendTaskPayloadDto;
import com.forA.chatbot.notification.dto.FcmNotificationRequestDto;
import com.forA.chatbot.notification.repository.DeviceTokenRepository;
import com.forA.chatbot.user.domain.User;
import io.micrometer.core.instrument.MeterRegistry;
import io.sentry.Sentry;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryReminderNotificationService {

  private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Seoul");
  private static final int MAX_RETRY_COUNT = 3;
  private static final Duration STALE_PROCESSING_JOB_THRESHOLD = Duration.ofMinutes(10);
  private static final List<DiaryReminderRescheduleJobStatus> RUNNABLE_STATUSES =
      List.of(DiaryReminderRescheduleJobStatus.PENDING,
          DiaryReminderRescheduleJobStatus.RETRY_WAITING);

  private final DiaryReminderRepository diaryReminderRepository;
  private final DiaryReminderRescheduleJobRepository rescheduleJobRepository;
  private final DeviceTokenRepository deviceTokenRepository;
  private final NotificationService notificationService;
  private final NotificationSendLogService sendLogService;
  private final CloudTasksClient cloudTasksClient;
  private final NotificationConverter notificationConverter;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;
  private final PlatformTransactionManager transactionManager;

  @Transactional(readOnly = true)
  public void scheduleDailyBatch(LocalDate scheduledDate) {
    List<DiaryReminder> reminders = diaryReminderRepository.findByEnabledTrue();
    List<Exception> failures = new ArrayList<>();
    List<Long> failedReminderIds = new ArrayList<>();

    for (DiaryReminder reminder : reminders) {
      LocalDate targetDate = scheduledDate != null
          ? scheduledDate
          : LocalDate.now(zoneIdOf(reminder.getUser()));
      try {
        scheduleTaskForReminder(reminder, targetDate);
      } catch (Exception e) {
        meterRegistry.counter(
            "chatbot.scheduler.diary_reminder.enqueue.total", "result", "failure")
            .increment();
        log.error("한줄일기 알림 task 생성 실패 - reminderId={}", reminder.getId(), e);
        failures.add(e);
        failedReminderIds.add(reminder.getId());
      }
    }

    if (!failures.isEmpty()) {
      IllegalStateException exception = new IllegalStateException(
          "Diary reminder daily batch failed for reminderIds=" + failedReminderIds);
      failures.forEach(exception::addSuppressed);
      throw exception;
    }
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleRescheduleRequested(DiaryReminderRescheduleRequestedEvent event) {
    processRescheduleJob(event.jobId());
  }

  public void processRunnableRescheduleJobs() {
    List<DiaryReminderRescheduleJob> jobs =
        rescheduleJobRepository.findRunnableJobs(RUNNABLE_STATUSES, LocalDateTime.now());

    for (DiaryReminderRescheduleJob job : jobs) {
      processRescheduleJob(job.getId());
    }
  }

  public int recoverStaleProcessingJobs() {
    return recoverStaleProcessingJobs(STALE_PROCESSING_JOB_THRESHOLD);
  }

  public int recoverStaleProcessingJobs(Duration staleThreshold) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime staleBefore = now.minus(staleThreshold);
    Integer recovered = transactionTemplate().execute(status ->
        rescheduleJobRepository.recoverStaleProcessingJobs(now, staleBefore));
    int recoveredCount = recovered == null ? 0 : recovered;
    if (recoveredCount > 0) {
      meterRegistry.counter(
          "chatbot.scheduler.diary_reminder.reschedule.recovered.total")
          .increment(recoveredCount);
      log.warn("오래된 PROCESSING 한줄일기 알림 재예약 job 복구 - count={}", recoveredCount);
    }
    return recoveredCount;
  }

  public void processRescheduleJob(Long jobId) {
    DiaryReminderRescheduleJob job = claimJob(jobId);
    if (job == null) {
      log.info("처리 가능한 한줄일기 알림 재예약 job이 아닙니다. jobId={}", jobId);
      return;
    }

    try {
      executeReschedule(job);
      markJobSucceeded(jobId);
    } catch (Exception e) {
      recordJobFailure(jobId, e);
    }
  }

  public void handleSendTask(Long reminderId, LocalDate scheduledDate) {
    DiaryReminder reminder = diaryReminderRepository.findById(reminderId).orElse(null);
    if (reminder == null || !reminder.isEnabled()) {
      log.info("한줄일기 알림 발송 no-op - reminderId={}", reminderId);
      return;
    }

    User user = reminder.getUser();
    LocalDate targetDate =
        scheduledDate != null ? scheduledDate : LocalDate.now(zoneIdOf(user));
    List<DeviceToken> enabledTokens = deviceTokenRepository.findByUserAndIsPushEnabledTrue(user);
    if (enabledTokens.isEmpty()) {
      log.debug("한줄일기 알림 발송 대상 토큰 없음 - userId={}", user.getId());
      return;
    }

    boolean anyFailed = false;
    for (DeviceToken deviceToken : enabledTokens) {
      if (!processSingleToken(reminder, deviceToken, targetDate)) {
        anyFailed = true;
      }
    }

    if (anyFailed) {
      throw new IllegalStateException(
          "At least one token delivery failed, retry Cloud Task for reminderId=" + reminderId);
    }
  }

  private void executeReschedule(DiaryReminderRescheduleJob job)
      throws JsonProcessingException {
    for (Long oldReminderId : job.getOldReminderIds()) {
      String taskId =
          CloudTasksClient.buildDiaryReminderTaskId(oldReminderId, job.getScheduledDate().toString());
      cloudTasksClient.deleteTask(taskId);
    }

    List<DiaryReminder> currentReminders =
        diaryReminderRepository.findEnabledByUserIdWithUserOrderByReminderTimeAsc(
            job.getUser().getId());
    for (DiaryReminder reminder : currentReminders) {
      scheduleTaskForReminder(reminder, job.getScheduledDate());
    }
  }

  private boolean scheduleTaskForReminder(DiaryReminder reminder, LocalDate scheduledDate)
      throws JsonProcessingException {
    Instant scheduleInstant = calculateScheduleInstant(reminder, scheduledDate);
    if (!scheduleInstant.isAfter(Instant.now())) {
      log.info("지난 한줄일기 알림 task는 생성하지 않습니다. reminderId={}, date={}",
          reminder.getId(), scheduledDate);
      return false;
    }

    String taskId =
        CloudTasksClient.buildDiaryReminderTaskId(reminder.getId(), scheduledDate.toString());
    String payload = objectMapper.writeValueAsString(
        new DiaryReminderSendTaskPayloadDto(reminder.getId(), scheduledDate));

    // CloudTasksClient가 AlreadyExists를 멱등 성공으로 처리하므로 그대로 호출한다.
    cloudTasksClient.createTask(taskId,
        "/internal/notifications/diary-reminders/send/" + reminder.getId(),
        payload,
        scheduleInstant);
    meterRegistry.counter(
        "chatbot.scheduler.diary_reminder.enqueue.total", "result", "success")
        .increment();
    return true;
  }

  private boolean processSingleToken(
      DiaryReminder reminder, DeviceToken deviceToken, LocalDate scheduledDate) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime lockUntil = now.plusMinutes(2);
    Long reminderId = reminder.getId();
    Long deviceTokenId = deviceToken.getId();

    NotificationSendLogService.ClaimResult claimResult = sendLogService.claim(
        NotificationType.DIARY_REMINDER, reminderId, deviceTokenId, scheduledDate, lockUntil);

    if (claimResult == NotificationSendLogService.ClaimResult.ALREADY_SENT) {
      return true;
    }
    if (claimResult != NotificationSendLogService.ClaimResult.CLAIMED) {
      return false;
    }

    FcmNotificationRequestDto request =
        notificationConverter.toDiaryReminderRequest(deviceToken);
    boolean success = notificationService.sendNotification(request);
    if (success) {
      sendLogService.markSent(
          NotificationType.DIARY_REMINDER, reminderId, deviceTokenId, scheduledDate, now);
      return true;
    }

    sendLogService.releaseClaim(
        NotificationType.DIARY_REMINDER,
        reminderId,
        deviceTokenId,
        scheduledDate,
        "FCM send returned false");
    return false;
  }

  private DiaryReminderRescheduleJob claimJob(Long jobId) {
    return transactionTemplate().execute(status -> {
      int updated =
          rescheduleJobRepository.claimRunnableJob(jobId, RUNNABLE_STATUSES, LocalDateTime.now());
      if (updated == 0) {
        return null;
      }
      return rescheduleJobRepository.findByIdWithUser(jobId).orElse(null);
    });
  }

  private void markJobSucceeded(Long jobId) {
    transactionTemplate().executeWithoutResult(status ->
        rescheduleJobRepository.findById(jobId).ifPresent(DiaryReminderRescheduleJob::markSucceeded));
    meterRegistry.counter(
        "chatbot.scheduler.diary_reminder.reschedule.total", "result", "success")
        .increment();
  }

  private void recordJobFailure(Long jobId, Exception e) {
    FailureResult result = transactionTemplate().execute(status -> {
      DiaryReminderRescheduleJob job = rescheduleJobRepository.findById(jobId).orElseThrow();
      long delayMinutes = nextDelayMinutes(job);
      boolean retryable = job.getRetryCount() < MAX_RETRY_COUNT;
      LocalDateTime nextRetryAt =
          retryable ? LocalDateTime.now().plusMinutes(delayMinutes) : null;
      job.recordFailure(shortError(e), nextRetryAt, retryable);
      return new FailureResult(job.getRetryCount(), job.getStatus(), delayMinutes);
    });

    meterRegistry.counter(
        "chatbot.scheduler.diary_reminder.reschedule.total", "result", "failure")
        .increment();
    log.error("한줄일기 알림 재예약 job 실패 - jobId={}, retryCount={}, status={}",
        jobId, result.retryCount(), result.status(), e);

    if (result.status() == DiaryReminderRescheduleJobStatus.RETRY_WAITING) {
      scheduleRetryTask(jobId, result.retryCount(), result.delayMinutes());
      return;
    }

    if (result.status() == DiaryReminderRescheduleJobStatus.FAILED) {
      Sentry.captureException(e);
    }
  }

  private void scheduleRetryTask(Long jobId, int retryCount, long delayMinutes) {
    try {
      String taskId = CloudTasksClient.buildDiaryReminderRescheduleTaskId(jobId, retryCount);
      cloudTasksClient.createTask(
          taskId,
          "/internal/notifications/diary-reminders/reschedule-jobs/" + jobId,
          "{}",
          Instant.now().plus(Duration.ofMinutes(delayMinutes)));
    } catch (Exception enqueueException) {
      transactionTemplate().executeWithoutResult(status ->
          rescheduleJobRepository.findById(jobId).ifPresent(job ->
              job.recordFailure(shortError(enqueueException), null, false)));
      Sentry.captureException(enqueueException);
      log.error("한줄일기 알림 재예약 retry task 생성 실패 - jobId={}", jobId, enqueueException);
    }
  }

  private long nextDelayMinutes(DiaryReminderRescheduleJob job) {
    return switch (job.getRetryCount()) {
      case 0 -> 1L;
      case 1 -> 5L;
      default -> 15L;
    };
  }

  private Instant calculateScheduleInstant(DiaryReminder reminder, LocalDate scheduledDate) {
    return ZonedDateTime.of(
        scheduledDate, reminder.getReminderTime(), zoneIdOf(reminder.getUser())).toInstant();
  }

  private ZoneId zoneIdOf(User user) {
    try {
      return ZoneId.of(user.getTimeZone());
    } catch (DateTimeException | NullPointerException e) {
      log.warn("사용자 timeZone이 유효하지 않아 기본 시간대를 사용합니다. userId={}", user.getId());
      return DEFAULT_ZONE_ID;
    }
  }

  private String shortError(Exception e) {
    String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    return message.length() <= 255 ? message : message.substring(0, 255);
  }

  private TransactionTemplate transactionTemplate() {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template;
  }

  private record FailureResult(
      int retryCount,
      DiaryReminderRescheduleJobStatus status,
      long delayMinutes
  ) {
  }
}
