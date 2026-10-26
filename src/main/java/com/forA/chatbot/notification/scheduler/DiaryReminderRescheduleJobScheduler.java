package com.forA.chatbot.notification.scheduler;

import com.forA.chatbot.notification.service.DiaryReminderNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiaryReminderRescheduleJobScheduler {

  private final DiaryReminderNotificationService diaryReminderNotificationService;

  @Scheduled(fixedDelayString = "${notification.diary-reminder.reschedule-scan-ms:60000}")
  public void poll() {
    diaryReminderNotificationService.recoverStaleProcessingJobs();
    diaryReminderNotificationService.processRunnableRescheduleJobs();
  }
}
