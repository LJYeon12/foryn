package com.forA.chatbot.notification.controller;

import com.forA.chatbot.apiPayload.ApiResponse;
import com.forA.chatbot.notification.dto.DiaryReminderSendTaskPayloadDto;
import com.forA.chatbot.notification.dto.SendTaskPayloadDto;
import com.forA.chatbot.notification.service.DiaryReminderNotificationService;
import com.forA.chatbot.notification.service.MedicationSchedulerService;
import com.forA.chatbot.notification.service.MedicationOutboxDispatcher;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

  private final MedicationSchedulerService medicationSchedulerService;
  private final DiaryReminderNotificationService diaryReminderNotificationService;
  private final MedicationOutboxDispatcher medicationOutboxDispatcher;

  @PostMapping("/schedule-daily")
  public ApiResponse<String> scheduleDailyBatch(
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate scheduledDate) {
    log.info("Daily batch 요청: scheduledDate={}", scheduledDate);
    List<Exception> failures = new ArrayList<>();
    try {
      medicationSchedulerService.scheduleDailyBatch(scheduledDate);
    } catch (Exception e) {
      failures.add(e);
      log.error("복약 알림 daily batch 실패", e);
    }
    try {
      diaryReminderNotificationService.scheduleDailyBatch(scheduledDate);
    } catch (Exception e) {
      failures.add(e);
      log.error("한줄일기 알림 daily batch 실패", e);
    }
    if (!failures.isEmpty()) {
      IllegalStateException exception = new IllegalStateException("Daily batch failed");
      failures.forEach(exception::addSuppressed);
      throw exception;
    }
    return ApiResponse.onSuccess("Daily batch 완료");
  }

  @PostMapping("/send/{bundleId}")
  public ApiResponse<String> sendNotification(
      @PathVariable Long bundleId,
      @RequestBody SendTaskPayloadDto payload) {
    log.info("알림 발송 요청: bundleId={}, scheduledDate={}", bundleId, payload.getScheduledDate());
    if (payload.getBundleId() != null && !bundleId.equals(payload.getBundleId())) {
      throw new IllegalArgumentException("Medication task target mismatch");
    }
    medicationSchedulerService.handleSendTask(bundleId, payload.getScheduledDate(), payload.getScheduleVersion());
    return ApiResponse.onSuccess("발송 처리 완료");
  }

  @PostMapping("/medication-outbox/recover")
  public ApiResponse<String> recoverMedicationOutbox() {
    medicationOutboxDispatcher.recoverAndDispatch();
    return ApiResponse.onSuccess("복약 예약 작업 복구 완료");
  }

  @PostMapping("/medication-outbox/{jobId}/retry")
  public ApiResponse<String> retryMedicationOutbox(@PathVariable Long jobId) {
    medicationOutboxDispatcher.retryFailed(jobId);
    return ApiResponse.onSuccess("복약 예약 작업 재시도 완료");
  }

  @PostMapping("/diary-reminders/send/{reminderId}")
  public ApiResponse<String> sendDiaryReminderNotification(
      @PathVariable Long reminderId,
      @RequestBody DiaryReminderSendTaskPayloadDto payload) {
    log.info("한줄일기 알림 발송 요청: reminderId={}, scheduledDate={}",
        reminderId, payload.getScheduledDate());
    diaryReminderNotificationService.handleSendTask(reminderId, payload.getScheduledDate());
    return ApiResponse.onSuccess("한줄일기 알림 발송 처리 완료");
  }

  @PostMapping("/diary-reminders/reschedule-jobs/{jobId}")
  public ApiResponse<String> processDiaryReminderRescheduleJob(@PathVariable Long jobId) {
    log.info("한줄일기 알림 재예약 job 처리 요청: jobId={}", jobId);
    diaryReminderNotificationService.processRescheduleJob(jobId);
    return ApiResponse.onSuccess("한줄일기 알림 재예약 처리 완료");
  }
}
