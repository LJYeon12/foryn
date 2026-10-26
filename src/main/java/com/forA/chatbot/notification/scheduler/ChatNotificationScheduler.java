package com.forA.chatbot.notification.scheduler;

import com.forA.chatbot.notification.service.NotificationService;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatNotificationScheduler {

  private final TaskScheduler taskScheduler;
  private final NotificationService notificationService;
  private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

  public void scheduleNotification(String sessionId, Long userId, String skillName) {
    // 이미 해당 세션에 대한 작업이 있다면 취소
    cancelNotification(sessionId);

    // 5분 뒤 실행 시간 설정
    Instant scheduledTime = Instant.now().plusSeconds(300);

    ScheduledFuture<?> future = taskScheduler.schedule(() -> {
      log.info("Executing scheduled notification for session: {}", sessionId);
      notificationService.sendChatReminderNotification(userId, skillName);
      // 작업 실행 후 맵에서 제거
      scheduledTasks.remove(sessionId);
    }, scheduledTime);


    scheduledTasks.put(sessionId, future);
    log.info("Scheduled a 5-minute reminder notification for session: {}", sessionId);
  }

  public void cancelNotification(String sessionId) {
    ScheduledFuture<?> future = scheduledTasks.get(sessionId);
    if (future != null) {
      future.cancel(false); // mayInterruptIfRunning = false: 실행 중인 작업은 중단하지 않는다.
      scheduledTasks.remove(sessionId);
      log.info("Canceled reminder notification for session: {}", sessionId);
    }
  }
}