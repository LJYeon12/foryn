package com.forA.chatbot.user.scheduler;

import com.forA.chatbot.auth.repository.UserRepository;
import com.forA.chatbot.user.domain.User;
import com.forA.chatbot.user.service.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserCleanupScheduler {

    private final UserRepository userRepository;
    private final UserService userService;
    private final MeterRegistry meterRegistry;

    /**
     * 매일 자정에 실행하여 논리 삭제 후 30일이 지난 사용자를 영구 삭제
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void cleanupExpiredUsers() {
        LocalDateTime expirationTime = LocalDateTime.now().minusDays(30);
        log.info("Soft Delete 만료 유저 정리 시작 (기준 시각: {})", expirationTime);
        Timer.Sample sample = Timer.start(meterRegistry);
        int successCount = 0;
        int failCount = 0;
        try {
            List<User> expiredUsers = userRepository.findExpiredSoftDeletedUsers(expirationTime);

            for (User user : expiredUsers) {
                try {
                    log.info("영구 삭제 진행: userId={}", user.getId());

                    userService.deleteUserRelatedData(user.getId());

                    userRepository.delete(user);

                    log.info("영구 삭제 완료: userId={}", user.getId());
                    successCount++;
                } catch (Exception e) {
                    log.error("유저 영구 삭제 중 오류 발생: userId={}", user.getId(), e);
                    failCount++;
                }
            }

            meterRegistry.counter("chatbot.scheduler.user.cleanup.runs.total", "result", "success").increment();
            log.info("Soft Delete 만료 유저 정리 완료. 총 삭제 건수: {}", successCount);
        } catch (Exception e) {
            meterRegistry.counter("chatbot.scheduler.user.cleanup.runs.total", "result", "failure").increment();
            throw e;
        } finally {
            meterRegistry.counter("chatbot.scheduler.user.cleanup.deleted.total", "result", "success").increment(successCount);
            meterRegistry.counter("chatbot.scheduler.user.cleanup.deleted.total", "result", "failure").increment(failCount);
            sample.stop(meterRegistry.timer("chatbot.scheduler.user.cleanup.duration"));
        }
    }
}
