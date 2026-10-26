package com.forA.chatbot.notification.service;

import com.forA.chatbot.medications.domain.MedicationBundle;
import com.forA.chatbot.medications.event.MedicationBundleChangedEvent;
import com.forA.chatbot.medications.repository.MedicationBundleRepository;
import com.forA.chatbot.medications.service.MedicationScheduleOutboxWriter;
import com.forA.chatbot.notification.converter.NotificationConverter;
import com.forA.chatbot.notification.domain.DeviceToken;
import com.forA.chatbot.notification.dto.FcmNotificationRequestDto;
import com.forA.chatbot.notification.repository.DeviceTokenRepository;
import com.forA.chatbot.user.domain.User;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationSchedulerService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final MedicationBundleRepository medicationBundleRepository;
  private final DeviceTokenRepository deviceTokenRepository;
  private final NotificationService notificationService;
  private final NotificationSendLogService sendLogService;
  private final MedicationScheduleOutboxWriter outboxWriter;
  private final MedicationOutboxDispatcher outboxDispatcher;
  private final MeterRegistry meterRegistry;
  private final NotificationConverter notificationConverter;

  @Transactional
  public void checkAndSendMedicationNotifications() {
    log.info("Medication notification scheduler started");
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      LocalTime executionTime = LocalTime.now().withSecond(0).withNano(0);
      String todayDayOfWeek = LocalDate.now().getDayOfWeek().toString();

      log.debug("Execution time: {}, dayOfWeek: {}", executionTime, todayDayOfWeek);

      List<MedicationBundle> bundles =
          medicationBundleRepository.findScheduledBundles(todayDayOfWeek, executionTime);

      log.info("Bundles to notify: {}", bundles.size());

      for (MedicationBundle bundle : bundles) {
        meterRegistry.counter("chatbot.scheduler.medication.bundles.processed").increment();
        try {
          sendMedicationNotificationDirect(bundle);
        } catch (Exception e) {
          log.error(
              "Medication notification send failed: bundleId={}, userId={}, error={}",
              bundle.getId(),
              bundle.getUser().getId(),
              e.getMessage(),
              e);
        }
      }

      meterRegistry.counter("chatbot.scheduler.medication.runs.total", "result", "success")
          .increment();
      log.info("Medication notification scheduler finished");
    } catch (Exception e) {
      meterRegistry.counter("chatbot.scheduler.medication.runs.total", "result", "failure")
          .increment();
      log.error("Medication notification scheduler failed", e);
      throw e;
    } finally {
      sample.stop(meterRegistry.timer("chatbot.scheduler.medication.duration"));
    }
  }

  public void scheduleDailyBatch(LocalDate scheduledDate) {
    LocalDate targetDate = scheduledDate != null ? scheduledDate : LocalDate.now(KST);
    String targetDayOfWeek = targetDate.getDayOfWeek().toString();
    List<String> failedBundles = new ArrayList<>();
    int createdTaskCount = 0;

    log.info("Daily batch started: date={}, dayOfWeek={}", targetDate, targetDayOfWeek);

    List<MedicationBundle> bundles =
        medicationBundleRepository.findByDayOfWeekContainingAndIsDeletedFalse(targetDayOfWeek);

    log.info("Cloud Task target bundle count: {}", bundles.size());

    for (MedicationBundle bundle : bundles) {
      try {
        outboxWriter.recordDaily(bundle.getId(), targetDate);
        createdTaskCount++;
      } catch (Exception e) {
        String failureSummary = String.format("bundleId=%d", bundle.getId());
        failedBundles.add(failureSummary);
        log.error("Medication outbox write failed: bundleId={}", bundle.getId());
      }
    }

    if (!failedBundles.isEmpty()) {
      throw new IllegalStateException(
          "Daily batch failed to enqueue bundles: " + String.join(", ", failedBundles));
    }

    log.info("Daily batch finished: {} schedule intents recorded", createdTaskCount);
  }

  public void handleSendTask(Long bundleId, LocalDate scheduledDate) {
    handleSendTask(bundleId, scheduledDate, null);
  }

  public void handleSendTask(Long bundleId, LocalDate scheduledDate, Long scheduleVersion) {
    LocalDate targetDate = scheduledDate != null ? scheduledDate : LocalDate.now(KST);
    MedicationBundle bundle = medicationBundleRepository.findById(bundleId).orElse(null);

    if (bundle == null || bundle.getIsDeleted()) {
      log.info("Deleted or missing bundle ignored: bundleId={}", bundleId);
      return;
    }

    // 버전이 없는 기존 작업은 마이그레이션된 계획의 버전이 0인 동안만 유효하다.
    long expectedVersion = scheduleVersion == null ? 0L : scheduleVersion;
    if (bundle.getScheduleVersion() != expectedVersion || !bundle.isScheduledOn(targetDate)
        || targetDate.isBefore(LocalDate.now(KST))) {
      log.info("Stale medication task ignored: bundleId={}, version={}", bundleId, expectedVersion);
      return;
    }
    if (targetDate.isAfter(LocalDate.now(KST))) {
      throw new IllegalStateException("Medication notification date is not due");
    }

    User user = bundle.getUser();
    List<DeviceToken> enabledTokens = deviceTokenRepository.findByUserAndIsPushEnabledTrue(user);

    if (enabledTokens.isEmpty()) {
      log.debug("No enabled device token found: userId={}", user.getId());
      return;
    }

    boolean anyFailed = false;
    for (DeviceToken deviceToken : enabledTokens) {
      if (!processSingleToken(bundle, deviceToken, targetDate)) {
        anyFailed = true;
      }
    }

    if (anyFailed) {
      throw new IllegalStateException(
          "At least one token delivery failed, retry Cloud Task for bundleId=" + bundleId);
    }
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleBundleChangedEvent(MedicationBundleChangedEvent event) {
    outboxDispatcher.dispatchBundle(event.getBundleId());
  }

  Instant calculateScheduleInstant(LocalDate scheduledDate, LocalTime utcTime) {
    LocalTime scheduledTimeKst = convertToLocal(utcTime);
    return ZonedDateTime.of(scheduledDate, scheduledTimeKst, KST).toInstant();
  }

  /**
   * 토큰별 발송 로그의 처리권을 얻은 뒤 발송 완료로 표시하거나 처리권을 해제한다.
   *
   * @return 발송했거나 이미 발송했으면 true, 재시도가 필요하면 false
   */
  private boolean processSingleToken(
      MedicationBundle bundle, DeviceToken deviceToken, LocalDate scheduledDate) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime lockUntil = now.plusMinutes(2);

    NotificationSendLogService.ClaimResult claimResult =
        sendLogService.claim(bundle.getId(), deviceToken.getId(), scheduledDate, lockUntil);

    if (claimResult == NotificationSendLogService.ClaimResult.ALREADY_SENT) {
      log.debug(
          "Skip token because send log is already sent: bundleId={}, deviceTokenId={}, date={}",
          bundle.getId(),
          deviceToken.getId(),
          scheduledDate);
      return true;
    }

    if (claimResult == NotificationSendLogService.ClaimResult.LOCKED_BY_ACTIVE_PROCESSOR) {
      log.warn(
          "Token delivery blocked by active send lock, retry required: bundleId={}, deviceTokenId={}, date={}",
          bundle.getId(),
          deviceToken.getId(),
          scheduledDate);
      return false;
    }

    if (claimResult == NotificationSendLogService.ClaimResult.RETRY_REQUIRED) {
      log.warn(
          "Token delivery requires retry because send ownership was not acquired: bundleId={}, deviceTokenId={}, date={}",
          bundle.getId(),
          deviceToken.getId(),
          scheduledDate);
      return false;
    }

    FcmNotificationRequestDto request =
        notificationConverter.toMedicationRequest(deviceToken, bundle);

    boolean success = notificationService.sendNotification(request);
    if (success) {
      sendLogService.markSent(bundle.getId(), deviceToken.getId(), scheduledDate, now);
      return true;
    }

    sendLogService.releaseClaim(
        bundle.getId(), deviceToken.getId(), scheduledDate, "FCM send returned false");
    return false;
  }

  private void sendMedicationNotificationDirect(MedicationBundle bundle) {
    User user = bundle.getUser();
    List<DeviceToken> enabledDeviceTokens =
        deviceTokenRepository.findByUserAndIsPushEnabledTrue(user);

    if (enabledDeviceTokens.isEmpty()) {
      log.debug("No enabled device token found: userId={}", user.getId());
      return;
    }

    for (DeviceToken deviceToken : enabledDeviceTokens) {
      FcmNotificationRequestDto request =
          notificationConverter.toMedicationRequest(deviceToken, bundle);

      boolean success = notificationService.sendNotification(request);
      if (success) {
        log.info(
            "Medication notification sent: userId={}, token={}",
            user.getId(),
            maskToken(deviceToken.getDeviceToken()));
      }
    }
  }

  private LocalTime convertToLocal(LocalTime utcTime) {
    return ZonedDateTime.of(LocalDate.now(KST), utcTime, ZoneId.of("UTC"))
        .withZoneSameInstant(KST)
        .toLocalTime();
  }

  private String maskToken(String token) {
    if (token == null || token.length() < 10) {
      return "***";
    }
    return token.substring(0, 10) + "***";
  }
}
