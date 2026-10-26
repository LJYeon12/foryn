package com.forA.chatbot.notification.scheduler;

import static org.mockito.Mockito.verify;

import com.forA.chatbot.notification.service.DiaryReminderNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiaryReminderRescheduleJobSchedulerTest {

  @Mock
  private DiaryReminderNotificationService diaryReminderNotificationService;

  @InjectMocks
  private DiaryReminderRescheduleJobScheduler scheduler;

  @Test
  void poll_recoversStaleJobsThenProcessesRunnableJobs() {
    scheduler.poll();

    verify(diaryReminderNotificationService).recoverStaleProcessingJobs();
    verify(diaryReminderNotificationService).processRunnableRescheduleJobs();
  }
}
