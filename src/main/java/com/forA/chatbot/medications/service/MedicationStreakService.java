package com.forA.chatbot.medications.service;

import com.forA.chatbot.medications.repository.MedicationLogRepository;
import com.forA.chatbot.user.domain.User;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationStreakService {

  private final MedicationLogRepository medicationLogRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 사용자가 약을 복용했을 때 호출하여 3일 연속 달성 여부를 확인하고 알림을 보냅니다.
   */
  @Transactional(readOnly = true)
  public void checkAndSendStreakNotification(User user, LocalDate date) {
    // 복약 체크한 날 기준 최근 3일 확인
    for (int i = 0; i < 3; i++) {
      LocalDate targetDate = date.minusDays(i);
      log.info("사용자 ID {}님 날짜 {} 복약 확인", user.getId(), targetDate);
      if (!isDayFullyAchieved(user, targetDate)) {
        log.info("사용자 ID {}님 날짜 {} 복약 실패", user.getId(), targetDate);
        return; // 하루라도 실패했다면 조기 종료
      }
    }

    // 3일 연속 성공 시 알림 발송 로직 호출
    log.info("사용자 {}님 3일 연속 복약 달성! 알림 발송을 시작합니다.", user.getId());
    sendStreakPush(user);
  }

  private boolean isDayFullyAchieved(User user, LocalDate date) {
    String dayOfWeekName = date.getDayOfWeek().name(); // "MONDAY", "TUESDAY" 등

    long totalToTake = medicationLogRepository.countTotalBundlesToTake(user, dayOfWeekName);
    log.info("{} 에 먹어야 하는 약 개수 : {}", dayOfWeekName, totalToTake);
    // 해당 요일에 설정된 약이 하나도 없다면 연속 성공으로 치지 않음
    if (totalToTake == 0)
      return false;

    long completedCount = medicationLogRepository.countCompletedLogs(user, date);
    log.info("{} 에 먹은 약 개수: {}", dayOfWeekName, completedCount);
    return totalToTake == completedCount;
  }

  private void sendStreakPush(User user) {
    eventPublisher.publishEvent(new com.forA.chatbot.notification.event.StreakAchievementEvent(user.getId()));
  }
}
