package com.forA.chatbot.medications.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.forA.chatbot.medications.repository.MedicationLogRepository;
import com.forA.chatbot.notification.event.StreakAchievementEvent;
import com.forA.chatbot.user.domain.User;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MedicationStreakServiceTest {

    @InjectMocks
    private MedicationStreakService streakService;

    @Mock
    private MedicationLogRepository medicationLogRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("3일 연속 복약 달성 시 StreakAchievementEvent가 발행되어야 한다")
    void checkAndSendStreakNotification_ShouldPublishEvent_WhenStreakAchieved() {
        // 준비
        Long userId = 1L;
        User user = User.builder().id(userId).email("test@example.com").build();
        LocalDate today = LocalDate.now();

        // 3일 연속 완료 상황 모킹
        for (int i = 0; i < 3; i++) {
            LocalDate targetDate = today.minusDays(i);
            String dayOfWeek = targetDate.getDayOfWeek().name();
            given(medicationLogRepository.countTotalBundlesToTake(user, dayOfWeek)).willReturn(2L);
            given(medicationLogRepository.countCompletedLogs(user, targetDate)).willReturn(2L);
        }

        // 실행
        streakService.checkAndSendStreakNotification(user, today);

        // 검증
        verify(eventPublisher).publishEvent(any(StreakAchievementEvent.class));
    }

    @Test
    @DisplayName("3일 연속 미달성 시 StreakAchievementEvent가 발행되지 않아야 한다")
    void checkAndSendStreakNotification_ShouldNotPublishEvent_WhenStreakNotAchieved() {
        // 준비
        User user = User.builder().id(1L).email("test@example.com").build();
        LocalDate today = LocalDate.now();

        // 오늘은 달성, 어제는 미달성 상황 모킹
        given(medicationLogRepository.countTotalBundlesToTake(user, today.getDayOfWeek().name())).willReturn(2L);
        given(medicationLogRepository.countCompletedLogs(user, today)).willReturn(1L); // 미달성

        // 실행
        streakService.checkAndSendStreakNotification(user, today);

        // 검증
        verify(eventPublisher, never()).publishEvent(any(StreakAchievementEvent.class));
    }

    @Test
    @DisplayName("해당 요일에 설정된 약이 없는 경우 3일 달성으로 치지 않고 이벤트를 발행하지 않아야 한다")
    void checkAndSendStreakNotification_ShouldNotPublishEvent_WhenNoBundlesConfigured() {
        // 준비
        User user = User.builder().id(1L).email("test@example.com").build();
        LocalDate today = LocalDate.now();

        // 오늘은 복약 설정 없음
        given(medicationLogRepository.countTotalBundlesToTake(user, today.getDayOfWeek().name())).willReturn(0L);

        // 실행
        streakService.checkAndSendStreakNotification(user, today);

        // 검증
        verify(eventPublisher, never()).publishEvent(any(StreakAchievementEvent.class));
    }
}
