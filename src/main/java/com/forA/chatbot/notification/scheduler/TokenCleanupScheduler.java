package com.forA.chatbot.notification.scheduler;

import com.forA.chatbot.notification.repository.DeviceTokenRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

  private final DeviceTokenRepository deviceTokenRepository;
  private final MeterRegistry meterRegistry;

  // 매일 새벽 4시에 실행
  @Scheduled(cron = "0 0 4 * * *")
  @Transactional
  public void cleanupStaleTokens() {
    log.info("Running scheduled job to delete stale FCM tokens...");
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      // 한 달(30일) 이상 갱신되지 않은 토큰을 삭제
      LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
      deviceTokenRepository.deleteByUpdatedAtBefore(thirtyDaysAgo);
      meterRegistry.counter("chatbot.scheduler.token.cleanup.runs.total", "result", "success").increment();
      log.info("Stale FCM token cleanup finished.");
    } catch (Exception e) {
      meterRegistry.counter("chatbot.scheduler.token.cleanup.runs.total", "result", "failure").increment();
      throw e;
    } finally {
      sample.stop(meterRegistry.timer("chatbot.scheduler.token.cleanup.duration"));
    }
  }
}
