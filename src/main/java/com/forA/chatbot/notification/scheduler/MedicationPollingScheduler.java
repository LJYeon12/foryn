package com.forA.chatbot.notification.scheduler;

import com.forA.chatbot.notification.service.MedicationSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(
    name = "notification.scheduling.mode",
    havingValue = "polling"
)
@Component
@RequiredArgsConstructor
public class MedicationPollingScheduler {

  private final MedicationSchedulerService schedulerService;

  @Scheduled(cron = "0 * * * * *")
  public void poll() {
    schedulerService.checkAndSendMedicationNotifications();
  }
}
