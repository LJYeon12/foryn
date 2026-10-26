package com.forA.chatbot.notification.event;

import com.forA.chatbot.notification.service.NotificationService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleStreakAchievementEvent(StreakAchievementEvent event) {
        log.info("[Async Event] 3일 연속 달성 이벤트 수신 - 사용자 ID: {}", event.getUserId());
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            notificationService.sendStreakAchievementNotification(event.getUserId());
            meterRegistry.counter("chatbot.event.streak.total", "result", "success").increment();
            log.info("[Async Event] 3일 연속 달성 알림 발송 완료 - 사용자 ID: {}", event.getUserId());
        } catch (Exception e) {
            meterRegistry.counter("chatbot.event.streak.total", "result", "failure").increment();
            log.error("[Async Event] 3일 연속 달성 알림 발송 중 에러 발생 (메인 트랜잭션 안전): {}", e.getMessage(), e);
        } finally {
            sample.stop(meterRegistry.timer("chatbot.event.streak.duration"));
        }
    }
}
